package validator;

import static validator.ConstraintVocabulary.NODE_PROPERTY_SEPARATOR_SYMBOL;

import java.util.HashSet;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
public class DataGraphRelationshipValidator {
  private static HashSet<String> relationshipAttributesToSkip = new HashSet<>(){{add("name");add("startLabel");add("endLabel"); add("directed");}};
  private static HashSet<String> constraintTypeToSkip = new HashSet<>(){{add("required");}};
  public static void validateRelationships(
      GraphDatabaseService schemaGraph, GraphDatabaseService dataGraph, long rootNodeId)
  {
    Transaction stx = schemaGraph.beginTx();
    for(Relationship rel : stx.getAllRelationships()){
      if(!rel.isType(RelationshipType.withName("$$childOf"))) {
        validateRelationshipsWithType(rel, dataGraph);
      }
    }
  }

  public static void validateRelationshipProperty(
      long relId, RelationshipType relType, boolean directed, String startLabel, String endLabel,
      Transaction tx, String constraintType, Object constraintValue, String key, Object value)
  {
    switch (constraintType) {
      case "unique":
        if ((boolean) constraintValue){
          Result res;
          if(directed) {
            res = tx.execute("MATCH (:"+startLabel+")-[r:" + relType.name() + " {" + key + ":" + value
                + "}]-(:"+endLabel+") RETURN Count(r) as c");
          }else{
            res = tx.execute("MATCH (:"+startLabel+")-[r:" + relType.name() + " {" + key + ":" + value
                + "}]->(:"+endLabel+") RETURN Count(r) as c");
          }
          long count = (long) res.next().get("c");
          if(count > 1){
            System.out.println(String.format(
                "\nNeo Validation Error: Relationship with ID %d and relationship type '%s' has property '%s' which should be unique, but %d duplicate(s) found", relId, relType, key,count ));
          }
        }
        break;
      case "type":
        switch ((String) constraintValue) {
          case ConstraintVocabulary.DECIMAL:
            try {
              long v =  (long) value;
            } catch (NumberFormatException e) {
              System.out.println(String.format(
                  "\nNeo Validation Error: Relationship with ID %d and relationship type '%s' has property '%s' with value '%s'. Value should be of type '%s' instead incompatible type was found", relId, relType, key, value, ConstraintVocabulary.DECIMAL));
            }
            break;
          case ConstraintVocabulary.INTEGER:
            try {
              int v =  (int)(long) value;
            } catch (NumberFormatException e) {
              System.out.println(String.format(
                  "\nNeo Validation Error: Relationship with ID %d and relationship type '%s' has property '%s' with value '%s'. Value should be of type '%s' instead incompatible type was found", relId, relType, key, value, ConstraintVocabulary.INTEGER));
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
    RelationshipType relType = schemaRel.getType();
    boolean directed = (boolean) schemaRel.getProperty("directed");
    for (ResourceIterator<Relationship> it = tx.findRelationships(relType); it.hasNext(); ) {
      Relationship rel = it.next();
      long relId = rel.getId();
      for(String prop : schemaRel.getPropertyKeys()){
        if(!relationshipAttributesToSkip.contains(prop)) {
          String[] constraint = prop.split(NODE_PROPERTY_SEPARATOR_SYMBOL);
          String key = constraint[0];
          Object constraintValue = schemaRel.getProperty(prop);
          String constraintType = constraint[1];
          Object value = rel.getProperty(key, null);
          if (!constraintTypeToSkip.contains(constraintType)) {
            if (value == null && (boolean) schemaRel.getProperty(
                key + NODE_PROPERTY_SEPARATOR_SYMBOL + " required",
                true)) {
              System.out.println(
                  String.format(
                      "\nNeo Validation Error: Failed to validate Relationship with id %d and type '%s'. This relationship failed on the '%s' constraint defined in the schema", relId, relType, constraintType));
            } else {
              validateRelationshipProperty(relId, relType, directed, (String) rel.getProperty("startLabel"), (String) rel.getProperty("endLabel"), tx, constraintType, constraintValue, key, value);
            }
          }
        }
      }
    }
  }

}
