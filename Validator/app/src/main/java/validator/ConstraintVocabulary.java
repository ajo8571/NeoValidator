package validator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConstraintVocabulary {
  public enum ConstraintTypes{
    integer,
    decimal,
    bool,
    string
  }

  //TYPES
  public static final String INTEGER = "int";
  public static final String DECIMAL = "long";
  public static final String BOOL = "bool";
  public static final String STRING = "string";

  // NAMING CONVENTIONS
  public static final String NODE_PROPERTY_SEPARATOR_SYMBOL = "%_";
  public static final String NODE_RELATIONSHIP_DEFINITION_SYMBOL = "$_$RELATIONSHIP$_$";


  //TYPE CONSTRAINTS
  public static final String LESS_THAN = "lt";
  public static final String LESS_THAN_OR_EQUAL = "lte";
  public static final String GREATER_THAN_OR_EQUAL = "gte";
  public static final String GREATER_THAN = "gt";
  public static final String EQUAL = "eq";
  public static final String LIKE = "like";
  public static final String MOD = "mod";
  public static final String ONE_OF = "oneof";




  public static final int MAX_CARDINALITY_INFINITY = -1;
  public static String getNodePropertyKey(String rel_name, boolean maxCardinality ){
    String key = NODE_RELATIONSHIP_DEFINITION_SYMBOL + NODE_PROPERTY_SEPARATOR_SYMBOL +rel_name+NODE_PROPERTY_SEPARATOR_SYMBOL;
    if(maxCardinality){
      return key+"maxCardinality";
    }
    return key + "minCardinality";
  }

  public static String getNodePropertyKey(String rel_name, String constraint ){
    return rel_name+NODE_PROPERTY_SEPARATOR_SYMBOL+constraint;
  }



  public final static Set<String> TYPES = new HashSet<>(){{add(INTEGER); add(DECIMAL); add(STRING); add(BOOL);}};
  public final static Map<String, HashSet<String>> TYPE_CONSTRAINTS = new HashMap<>(){
    {
      put(INTEGER, new HashSet<>(){{add(EQUAL);add(GREATER_THAN); add(LESS_THAN); add(GREATER_THAN_OR_EQUAL); add(LESS_THAN_OR_EQUAL);add(MOD);add(ONE_OF);}});
      put(BOOL, new HashSet<>(){{add(EQUAL);}});
      put(STRING, new HashSet<>(){{add(EQUAL);add(GREATER_THAN); add(LESS_THAN); add(GREATER_THAN_OR_EQUAL); add(LESS_THAN_OR_EQUAL);add(LIKE);add(ONE_OF);}});
      put(DECIMAL, new HashSet<>(){{add(EQUAL);add(GREATER_THAN); add(LESS_THAN); add(GREATER_THAN_OR_EQUAL); add(LESS_THAN_OR_EQUAL);add(ONE_OF);}});

    }
  };
}
