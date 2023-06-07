package validator;

import static helpers.GraphBuilder.deleteDir;

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
        node.setProperty(name+"%_type", type);
        node.setProperty(name+"%_unique",property.getOrDefault("unique", false));
        node.setProperty(name+"%_required",property.getOrDefault("required", true));
        if(property.containsKey("type_constraint")){
          Map<String, Object> tc = (Map<String, Object>) property.get("type_constraint");
          for(String key : tc.keySet() ){
            if(ConstraintVocabulary.TYPE_CONSTRAINTS.get(type).contains(key)){
              node.setProperty(name+"%_"+key, tc.get(key));
            }else{
              throw new Exception(String.format(
                  "\nNeoValidator Schema Exception: Invalid type constraint '%s' for type '%s' in constraint definition for label '%s'", key, type, label));
            }
          }
        }
        node.setProperty(name+"%_required",property.getOrDefault("required", true));
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
      Integer maxCardinality = null;
      int minCardinality = 1;
      if(rel.containsKey("maxCardinality")) {
        try {
          maxCardinality = (int) rel.get("maxCardinality");
        } catch (NumberFormatException e) {
          throw new Exception(
              "\nNeoValidator Schema Exception: maxCardinality for relationship with name " + name
                  + " must be an integer");
        }
      }
      if(rel.containsKey("minCardinality")) {
        try {
          minCardinality = (int)rel.get("minCardinality");
        } catch (NumberFormatException e) {
          throw new Exception(
              "\nNeoValidator Schema Exception: minCardinality for relationship with name " + name
                  + " must be an integer");
        }
      }
      Node startNode = tx.getNodeById(labelIdMap.get(startLabel));
      Node endNode = tx.getNodeById(labelIdMap.get(endLabel));
      Relationship r = startNode.createRelationshipTo(endNode, RelationshipType.withName(name));
      r.setProperty("maxCardinality", maxCardinality);
      r.setProperty("minCardinality", minCardinality);
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
          r.setProperty(propName+"%_type", prop.get("type"));
          r.setProperty(propName+"%_required", prop.getOrDefault("required", true));
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
