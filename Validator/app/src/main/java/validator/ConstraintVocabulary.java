package validator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConstraintVocabulary {
  public enum ConstraintTypes{
    number,
    bool,
    string
  }

  public static final String NUMBER = "number";
  public static final String BOOL = "bool";
  public static final String STRING = "string";
  public static final String NODE_PROPERTY_SEPARATOR_SYMBOL = "%_";
  public static final String NODE_RELATIONSHIP_DEFINITION_SYMBOL = "$_$RELATIONSHIP$_$";

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



  public final static Set<String> TYPES = new HashSet<>(){{add(NUMBER); add(STRING); add(BOOL);}};
  public final static Map<String, HashSet<String>> TYPE_CONSTRAINTS = new HashMap<>(){
    {
      put(NUMBER, new HashSet<>(){{add("eq");add("gt"); add("lt"); add("gte"); add("lte");}});
      put(BOOL, new HashSet<>(){{add("eq");}});
      put(STRING, new HashSet<>(){{add("eq");add("like");}});
    }
  };
}
