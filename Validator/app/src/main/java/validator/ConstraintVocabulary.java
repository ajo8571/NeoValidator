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
  public static final String PROPERTY_SEPARATOR_SYMBOL = "%_";

  public final static Set<String> TYPES = new HashSet<>(){{add(NUMBER); add(STRING); add(BOOL);}};
  public final static Map<String, HashSet<String>> TYPE_CONSTRAINTS = new HashMap<>(){
    {
      put(NUMBER, new HashSet<>(){{add("eq");add("gt"); add("lt"); add("gte"); add("lte");}});
      put(BOOL, new HashSet<>(){{add("eq");}});
      put(STRING, new HashSet<>(){{add("eq");add("like");}});
    }
  };
}
