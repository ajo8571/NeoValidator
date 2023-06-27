package validator;

import static validator.ConstraintVocabulary.NODE_PROPERTY_SEPARATOR_SYMBOL;
import static validator.DataGraphNodeValidator.validateDataNodeProperty;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

public class DataGraphRelationshipValidator {

  private static HashSet<String> relationshipAttributesToSkip = new HashSet<>(){{add("name");add("startLabel");add("endLabel");add("maxCardinality");add("minCardinality");add("directed");}};
  private static HashSet<String> constraintTypeToSkip = new HashSet<>(){{add("required");}};

  //todo the commented code explores applying inheritance to relationships, keep or trash?
//  private static void getLabelForRelationshipValidation(ArrayList<Node> stack,
//      GraphDatabaseService dataGraph) {
//    for(Relationship rel : stack.get(0).getRelationships(Direction.OUTGOING,
//        RelationshipType.withName(SchemaParser.CHILD_OF_RELATIONSHIP))){
//      Node child = rel.getEndNode();
//      stack.add(0,child);
//      getLabelForRelationshipValidation(stack, dataGraph);
//    }
//    Node current = stack.get(0);
//    validateLabelForRelationships(current, stack, dataGraph);
//    stack.remove(0);
//  }
//
//  private static void validateLabelForRelationships(Node current, ArrayList<Node> stack, GraphDatabaseService dataGraph) {
//    Transaction tx = dataGraph.beginTx();
//    String label = current.getLabels().iterator().next().name();
//    Result res = tx.execute("MATCH (a:`"+label+"`) RETURN a");
//    while(res.hasNext()){
//      Map<String, Object> row = res.next();
//      validateSchemaStackForRelationships(stack, label, (Node) row.get("a"));
//      //System.out.println(((Node) row.getOrDefault("a", null)).getLabels());
//    }
//    tx.commit();
//    tx.close();
//  }
//
//  private static void validateSchemaStackForRelationships(ArrayList<Node> schemaNodes, String label, Node dataNode) {
//    for(Node schemaNode : schemaNodes){
//      for(Relationship rel : schemaNode.getRelationships(Direction.OUTGOING)) {
//        if(!rel.getType().name().equals(SchemaParser.CHILD_OF_RELATIONSHIP)) {
//          System.out.println(
//              "\nvalidating relationship with name: " + rel.getType().name() + " for label "
//                  + label);
//          validateRelationship(rel, label, dataNode);
//        }
//      }
//    }
//  }
//
//  private static void validateRelationship(Relationship rel, String label, Node dataNode) {
//    if(dataNode.hasRelationship(Direction.OUTGOING, rel.getType())) {
//      for(Relationship dataNodeRel : dataNode.getRelationships(rel.getType())) {
//        for (String prop : rel.getPropertyKeys()) {
//          if(!relationshipAttributesToSkip.contains(prop)) {
//            String[] constraint = prop.split(NODE_PROPERTY_SEPARATOR_SYMBOL );
//            String key = constraint[0];
//            String constraint_type = constraint[1];
//            Object constraint_value = rel.getProperty(prop);
//            Object value = dataNodeRel.getProperty(key, null);
//            if (!constraintTypeToSkip.contains(constraint_type)) {
//              if (value == null && (boolean) rel.getProperty(key + NODE_PROPERTY_SEPARATOR_SYMBOL + "  required",
//                  true)) {
//                System.out.println(
//                    new ValidationFailureReport(label, rel.getType().name(),
//                        key,
//                        "required", (String) value, dataNode.getId()));
//              } else {
//                validateDataNodeProperty(constraint_type, constraint_value, label, key, value,
//                    rel.getType().name(), dataNode);
//
//              }
//            }
//          }
//        }
//      }
//    }
//  }


//  public static void validateRelationships(GraphDatabaseService schemaGraph,
//      GraphDatabaseService dataGraph, long rootNodeId) {
//    Transaction stx = schemaGraph.beginTx();
//    ArrayList<Node> stack = new ArrayList<>();
//    stack.add(stx.getNodeById(rootNodeId));
//    getLabelForRelationshipValidation(stack, dataGraph);
//    //checkForUniqueness(dataGraph);
//  }

  public static void validateRelationships(GraphDatabaseService schemaGraph,
      GraphDatabaseService dataGraph, long rootNodeId) {
    Transaction stx = schemaGraph.beginTx();
    for(Relationship rel : stx.getAllRelationships()){
      validateRelationshipsWithType(rel, dataGraph);

    }
    //checkForUniqueness(dataGraph);
  }

  public static void validateRelationshipProperty(String constraint_type, Object constraint_value,
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

  private static void validateRelationshipsWithType(Relationship schemaRel, GraphDatabaseService dataGraph) {
    Transaction tx = dataGraph.beginTx();
    for (ResourceIterator<Relationship> it = tx.findRelationships(schemaRel.getType()); it.hasNext(); ) {
      Relationship rel = it.next();
      for(String prop : schemaRel.getPropertyKeys()){
        String[] constraint = prop.split(NODE_PROPERTY_SEPARATOR_SYMBOL);
        String key = constraint[0];
        Object constraintValue = schemaRel.getProperty(prop);
        String constraint_type = constraint[1];
        Object value = rel.getProperty(key, null);
        if (!constraintTypeToSkip.contains(constraint_type)) {
          if (value == null && (boolean) schemaRel.getProperty(
              key + NODE_PROPERTY_SEPARATOR_SYMBOL + " required",
              true)) {
            System.out.println("\nNeo Validation Error: Failed to validate Relationship with id %d and type '%s'.\n "
                + "This relationship failed on the '%s' constraint defined in the schema");
          } else {
            validateDataNodeProperty(constraint_type, constraintValue, label, key, value,
                schemaNode.getLabels().iterator().next().name(), dataNode);
          }
        }
      }
    }
  }

}
