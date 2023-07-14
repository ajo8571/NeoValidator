package validator;

import static helpers.GraphBuilder.deleteDir;
import static validator.ConstraintVocabulary.MAX_CARDINALITY_INFINITY;
import static validator.ConstraintVocabulary.NODE_PROPERTY_SEPARATOR_SYMBOL;
import static validator.ConstraintVocabulary.getNodePropertyKey;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

public class SchemaParser {

  public static final String CHILD_OF_RELATIONSHIP = "$$childOf";
  public static final String ROOT_NODE_LABEL = "$$root$$";

  static void checkForCycles(Node node, Set<Long> stack, Set<Long> seen) throws Exception {
    seen.add(node.getId());
    for (Relationship rel : node.getRelationships(Direction.OUTGOING,
        RelationshipType.withName(CHILD_OF_RELATIONSHIP))){
      Node child = rel.getEndNode();
      if (stack.contains(child.getId())){
        throw new Exception(String.format(
            "\nNeoValidator Schema Exception: Cyclic relationship cannot exsist in schema graph. Label '%s' is a parent_label of Label '%s' and vice versa", child.getLabels(), node.getLabels()));
      }
      stack.add(child.getId());
      checkForCycles(child, stack, seen);
    }
    stack.remove(node.getId());
  }
  public static void verifySchemaGraph(Transaction tx) throws Exception {
    //check for cycles
    Set<Long> seen = new HashSet<>();
    for(Node node : tx.getAllNodes()){
      if (!seen.contains(node.getId())){
        Set<Long> stack = new HashSet<>();
        stack.add(node.getId());
        seen.add(node.getId());
        checkForCycles(node, stack,seen);
      }
    }
  }

  public static void createSchemaNodesWithConstraintsAsProperties(Node node, Map<String, Object> obj, String label)
      throws Exception {
    if(obj.containsKey("properties")) {
      ArrayList<Map<String, Object>> properties = (ArrayList<Map<String, Object>>) obj.get("properties");
      for(Map<String, Object>property : properties){
        String name = (String) property.get("name");
        String type = (String) property.get("type");
        if(!ConstraintVocabulary.TYPES.contains(type)){
          throw new Exception(String.format(
              "\nNeoValidator Schema Exception: Invalid property type '%s' used in constraint definition for label '%s'", type, label));
        }
        JSONObject typeConstraints =  (JSONObject) property.getOrDefault("type_constraints", null);
        if(typeConstraints!= null) {
          for (String key : typeConstraints.keySet()) {
            if (!ConstraintVocabulary.TYPE_CONSTRAINTS.get(type).contains(key)) {
              throw new Exception(String.format(
                  "\nNeoValidator Schema Exception: Invalid property type constraint '%s' for "
                      + "property '%s' of type '%s' in property label '%s'", key, name, type,
                  label));
            }
            node.setProperty(getNodePropertyKey(name, key), typeConstraints.get(key));
          }
        }
        node.setProperty(getNodePropertyKey(name,"type"), type);
        node.setProperty(getNodePropertyKey(name, "unique"),property.getOrDefault("unique", false));
        node.setProperty(getNodePropertyKey(name,"required"), property.getOrDefault("required", true));

        if(property.containsKey("type_constraint")){
          Map<String, Object> tc = (Map<String, Object>) property.get("type_constraint");
          for(String key : tc.keySet() ){
            if(ConstraintVocabulary.TYPE_CONSTRAINTS.get(type).contains(key)){
              node.setProperty(name+NODE_PROPERTY_SEPARATOR_SYMBOL + ""+key, tc.get(key));
            }else{
              throw new Exception(String.format(
                  "\nNeoValidator Schema Exception: Invalid type constraint '%s' for type '%s' in constraint definition for label '%s'", key, type, label));
            }
          }
        }
      }
      if(obj.containsKey("relationships")){
        ArrayList<Map<String, Object>> rels = (ArrayList<Map<String, Object>>) obj.get("relationships");
        for(int i =0 ; i < rels.size(); i++ ){
          Map<String, Object> rel = rels.get(i);
          String rel_name = (String) rel.getOrDefault("name", null);
          if (rel_name == null){
            throw new Exception(String.format(
                "\nNeoValidator Schema Exception: relationship at position %d for label '%s' must contain a name", i, label));
          }
          try{
            int maxCardinality = (int)rel.getOrDefault("maxCardinality", MAX_CARDINALITY_INFINITY);
            int  minCardinality = (int) rel.getOrDefault("minCardinality", 0);
            if(maxCardinality < MAX_CARDINALITY_INFINITY || minCardinality < 0){
              throw new NumberFormatException();
            }else if(maxCardinality != MAX_CARDINALITY_INFINITY && maxCardinality < minCardinality){
              throw new Exception(String.format(
                  "\nNeoValidator Schema Exception: maxCardinality must be less than or equal to minCardinality\nIssue occurred at label '%s' with relationship at position %d", label, i));

            }
            node.setProperty(getNodePropertyKey(rel_name, true), maxCardinality);
            node.setProperty(getNodePropertyKey(rel_name, false), minCardinality);
          }catch (NumberFormatException e){
            throw new Exception(String.format(
                "\nNeoValidator Schema Exception: maxCardinality and minCardinality must be positive integers\nIssue occurred at label '%s' with relationship at position %d", label, i));
          }
        }
      }

    }
  }


  public static long parseConstraintsAndBuildSchemaGraph(String pathToConstraints, File schemaGraphFile, boolean deleteSchemaIfExists, GraphDatabaseService schemaGraph)
      throws Exception {
    JSONObject constraints = new JSONObject(new JSONTokener(new FileReader(pathToConstraints)));
    JSONArray labelConstraints = constraints.getJSONArray("labels");
    JSONArray relationshipConstraints = constraints.getJSONArray("relationships");
    Map<String, Long> labelIdMap = new HashMap<>();
    Set<String> seenLabels = new HashSet<>();
    if (deleteSchemaIfExists){
      deleteDir(schemaGraphFile);
    }
    long rootNodeId = createNodes(schemaGraph, labelIdMap, labelConstraints, seenLabels);
    createRelationships(schemaGraph, labelIdMap, relationshipConstraints);
    return rootNodeId;
  }

  private static void createRelationships(GraphDatabaseService schemaGraph,
      Map<String, Long> labelIdMap, JSONArray relationshipConstraints) throws Exception {
    Transaction tx = schemaGraph.beginTx();
    for (int i = 0; i < relationshipConstraints.length(); i++){
      Map<String, Object> rel = relationshipConstraints.getJSONObject(i).toMap();
      //TODO name may not be required for relationships
      if(!rel.containsKey("name")){
        throw new Exception("\nNeoValidator Schema Exception: field 'name' is required when defining relationship object");
      }
      if(!rel.containsKey("startLabel")) {
        throw new Exception("\nNeoValidator Schema Exception: field 'startLabel' is required when defining relationship object");
      }
      if(!rel.containsKey("endLabel")){
        throw new Exception("\nNeoValidator Schema Exception: field 'endLabel' is required when defining relationship object");
      }

      String startLabel = (String) rel.get("startLabel");
      String endLabel = (String) rel.get("endLabel");;
      String name = (String) rel.get("name");
      Node startNode = tx.getNodeById(labelIdMap.get(startLabel));
      Node endNode = tx.getNodeById(labelIdMap.get(endLabel));
      Relationship r = startNode.createRelationshipTo(endNode, RelationshipType.withName(name));
      r.setProperty("directed", rel.getOrDefault("directed", false));
      r.setProperty("startLabel", startLabel);
      r.setProperty("endLabel", endLabel);
      if(rel.containsKey("properties")){
        ArrayList<Map<String, Object>> properties = (ArrayList<Map<String, Object>>) rel.get("properties");
        for (int j = 0; j < properties.size(); j++){
          Map<String, Object> prop = properties.get(i);
          if(!prop.containsKey("name")){
            throw new Exception("\nNeoValidator Schema Exception: Relationship with name "+ name+" has a property with no name at position "+j);
          }
          if(!prop.containsKey("type")){
            throw new Exception("\nNeoValidator Schema Exception: Relationship with name "+ name+" has a property with name "+name+" has no type");
          }
          String propName = (String) prop.get("name");
          r.setProperty(propName+NODE_PROPERTY_SEPARATOR_SYMBOL + "type", prop.get("type"));
          r.setProperty(propName+NODE_PROPERTY_SEPARATOR_SYMBOL + "required", prop.getOrDefault("required", true));
        }
      }
    }
    tx.commit();
    tx.close();
  }

  private static long createNodes(GraphDatabaseService schemaGraph, Map<String, Long> labelIdMap,
      JSONArray labelConstraints, Set<String> seenLabels) throws Exception {
    Transaction tx = schemaGraph.beginTx();
    Node rootNode = tx.createNode(Label.label(ROOT_NODE_LABEL));
    labelIdMap.put(ROOT_NODE_LABEL, rootNode.getId());

    //create nodes and add constraints.
    for (int i = 0; i < labelConstraints.length(); i++){
      Map<String, Object> obj = labelConstraints.getJSONObject(i).toMap();
      String label = (String) obj.get("name");
      if(seenLabels.contains(label)){
        throw new Exception(String.format(
            "\nNeoValidator Schema Exception: Every label definition must be unique! Duplicate found for label '%s'", label));
      }else{
        seenLabels.add(label);
      }
      Node node = tx.createNode(Label.label(label));
      labelIdMap.put(label, node.getId());
      createSchemaNodesWithConstraintsAsProperties(node, obj, label);
    }

    //create nodes and add child of relationships
    for (int i = 0; i < labelConstraints.length(); i++){
      Map<String, Object> obj = labelConstraints.getJSONObject(i).toMap();
      String label = (String) obj.get("name");
      String parentLabel = (String) obj.getOrDefault("parent_label", ROOT_NODE_LABEL);
      if(!labelIdMap.containsKey(parentLabel)){
        throw new Exception(String.format(
            "\nNeoValidator Schema Exception: parent_label '%s' for label '%s' not in schema", parentLabel, label));
      }
      Node child = tx.getNodeById(labelIdMap.get(label));
      Node parent = tx.getNodeById(labelIdMap.get(parentLabel));
      parent.createRelationshipTo(child, RelationshipType.withName(CHILD_OF_RELATIONSHIP));
    }
    verifySchemaGraph(tx);
    tx.commit();
    tx.close();
    return rootNode.getId();
  }

}
