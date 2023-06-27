package validator;

import static validator.ConstraintVocabulary.NODE_PROPERTY_SEPARATOR_SYMBOL;
import static validator.ConstraintVocabulary.NODE_RELATIONSHIP_DEFINITION_SYMBOL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

public class DataGraphNodeValidator {

  private static HashSet<String> constraintTypeToSkip = new HashSet<>(){{add("required");}};
  private static Map<String, HashSet<String>> attributesToCheckForUniqueness = new HashMap<>();


  public static void validateDataGraphNodes(GraphDatabaseService schemaGraph,
      GraphDatabaseService dataGraph, long rootNodeId) {
    Transaction stx = schemaGraph.beginTx();
    ArrayList<Node> stack = new ArrayList<>();
    stack.add(stx.getNodeById(rootNodeId));
    getLabelForNodeValidation(stack, dataGraph);
    checkForNodeAttributeUniqueness(dataGraph);
  }

  private static void getLabelForNodeValidation(ArrayList<Node> stack,
      GraphDatabaseService dataGraph) {
    for(Relationship rel : stack.get(0).getRelationships(Direction.OUTGOING,
        RelationshipType.withName(SchemaParser.CHILD_OF_RELATIONSHIP))){
      Node child = rel.getEndNode();
      stack.add(0,child);
      getLabelForNodeValidation(stack, dataGraph);
    }
    Node current = stack.get(0);
    if(!current.getLabels().iterator().next().name().equals(SchemaParser.ROOT_NODE_LABEL)) {
      System.out.println("\nvalidating Label: " + current.getLabels() + "...");
      validateLabelForNodes(current, stack, dataGraph);
    }
    stack.remove(0);
  }

  private static void validateLabelForNodes(Node current, ArrayList<Node> stack,
      GraphDatabaseService dataGraph) {
    Transaction tx = dataGraph.beginTx();
    String label = current.getLabels().iterator().next().name();
    Result res = tx.execute("MATCH (a:`"+label+"`) RETURN a");
    while(res.hasNext()){
      Map<String, Object> row = res.next();
      validateSchemaStack(stack, label, (Node) row.get("a"));
    }
    tx.commit();
    tx.close();

  }

  private static void validateSchemaStack(ArrayList<Node> schemaNodes,String label, Node dataNode) {
    for(Node schemaNode : schemaNodes){
      validateDataNode(schemaNode, label, dataNode);
    }
  }

  private static void validateDataNode(Node schemaNode, String label, Node dataNode) {
    for(String prop: schemaNode.getPropertyKeys()) {
      String[] constraint = prop.split(NODE_PROPERTY_SEPARATOR_SYMBOL);
      String key = constraint[0];
      Object constraintValue = schemaNode.getProperty(prop);
      if (key.equals(NODE_RELATIONSHIP_DEFINITION_SYMBOL)) {
        validateDataNodeRelationshipConstraint(label, constraint[1], constraint[2], (int)constraintValue, dataNode );
      } else {
        String constraint_type = constraint[1];
        Object value = dataNode.getProperty(key, null);
        if (!constraintTypeToSkip.contains(constraint_type)) {
          if (value == null && (boolean) schemaNode.getProperty(
              key + NODE_PROPERTY_SEPARATOR_SYMBOL + " required",
              true)) {
            System.out.println(
                new ValidationFailureReport(label, schemaNode.getLabels().iterator().next().name(),
                    key,
                    "required", (String) value, dataNode.getId()));
          } else {
            validateDataNodeProperty(constraint_type, constraintValue, label, key, value,
                schemaNode.getLabels().iterator().next().name(), dataNode);

          }
        }
      }
    }
  }

  public static void validateDataNodeProperty(String constraint_type, Object constraint_value,
      String dataNodelabel, String key, Object value, String schemaNodeLabel, Node dataNode){
    switch (constraint_type) {
      case "unique":
        if ((boolean) constraint_value){
          if(!attributesToCheckForUniqueness.containsKey(dataNodelabel)) {
            attributesToCheckForUniqueness.put(dataNodelabel, new HashSet<>());
          }
          attributesToCheckForUniqueness.get(dataNodelabel).add(key);
        }
        break;
      case "type":
        switch ((String) constraint_value) {
          case ConstraintVocabulary.NUMBER:
            try {
              long v =  (long) value;
            } catch (NumberFormatException e) {
              System.out.println(new ValidationFailureReport(dataNodelabel,
                  schemaNodeLabel, key, constraint_type,
                  (String) value,
                  dataNode.getId()));
            }
            break;
          case ConstraintVocabulary.BOOL:
            Boolean v = Boolean.parseBoolean((String) value);
            break;
        }
        break;
    }
  }

  private static void validateDataNodeRelationshipConstraint(String ancestorLabel, String rel_name, String cardinalityType, int expectedCardinality,
      Node dataNode) {
    Iterable<Relationship> rels = dataNode.getRelationships(RelationshipType.withName(rel_name));
    int count = 0;
    if(cardinalityType.equals("maxCardinality")){
      for(Relationship rel : rels){
        count+=1;
        if(count > expectedCardinality){
          System.out.println(
              new ValidationFailureReport(dataNode.getLabels().iterator().next().name(),
                  ancestorLabel, "maxCardinality of relationship with name "+rel_name,
                  "maxCardinality", "greater than "+expectedCardinality,
                  dataNode.getId()
              ));
          break;
        }
      }
    }else{
      for(Relationship rel : rels){
        count+=1;
        if(count < expectedCardinality){
          System.out.println(
              new ValidationFailureReport(dataNode.getLabels().iterator().next().name(),
                  ancestorLabel, "minCardinality of relationship with name "+rel_name,
                  "minCardinality", "less than "+expectedCardinality,
                  dataNode.getId()
              ));
          break;
        }
      }
    }

  }

  private static void checkForNodeAttributeUniqueness(GraphDatabaseService dataGraph) {
    System.out.println("validating uniqueness constraint for all attributes...");
    Transaction tx = dataGraph.beginTx();
    for(String label : attributesToCheckForUniqueness.keySet()){
      for(String attribute : attributesToCheckForUniqueness.get(label)) {
        Result r = tx.execute("MATCH (a:" + label + ") with a."+attribute+" as attr, "
            + "count(*) as cnt where cnt > 1 RETURN attr, cnt");
        if(r.hasNext()){
          while(r.hasNext()) {
            Map<String, Object> row = r.next();
            System.out.println(
                ValidationFailureReport.uniqueValidationError(attribute, (String) row.get("attr"),
                    label, (long) row.get("cnt")));
          }
        }
      }
    }
  }

}
