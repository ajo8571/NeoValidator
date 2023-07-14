package validator;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import scala.Int;


public class ConstraintResolver {


  public static Map<String, BiFunction<Object, Object, Boolean>> constraintResolutionMap = Map.ofEntries(
      Map.entry(ConstraintVocabulary.LIKE, (schemaValue, actualValue ) -> {
        String sv = (String) schemaValue;
        String av = (String) actualValue;
        Pattern pattern = Pattern.compile(sv);
        Matcher matcher = pattern.matcher(av);
        return matcher.find();
      }),

      Map.entry(ConstraintVocabulary.EQUAL, Object::equals),
      Map.entry(ConstraintVocabulary.LESS_THAN, (schemaValue, actualValue ) -> {
        if(schemaValue instanceof Integer){
          return (int) schemaValue < (int) actualValue;
        } else if (schemaValue instanceof Long) {
          return (long) schemaValue < (long) actualValue;
        }
        return ((String) schemaValue).compareTo((String) actualValue) < 0;
      }),

      Map.entry(ConstraintVocabulary.GREATER_THAN, (schemaValue, actualValue ) -> {
        if(schemaValue instanceof Integer){
          return (int) schemaValue > (int) actualValue;
        } else if (schemaValue instanceof Long) {
          return (long) schemaValue > (long) actualValue;
        }
        return ((String) schemaValue).compareTo((String) actualValue) > 0;
      }),

      Map.entry(ConstraintVocabulary.LESS_THAN_OR_EQUAL, (schemaValue, actualValue ) -> {
        if(schemaValue instanceof Integer){
          return (int) schemaValue <= (int) actualValue;
        } else if (schemaValue instanceof Long) {
          return (long) schemaValue <= (long) actualValue;
        }
        return ((String) schemaValue).compareTo((String) actualValue) <= 0;
      }),

      Map.entry(ConstraintVocabulary.GREATER_THAN_OR_EQUAL, (schemaValue, actualValue ) -> {
        if(schemaValue instanceof Integer){
          return (int) schemaValue >= (int) actualValue;
        } else if (schemaValue instanceof Long) {
          return (long) schemaValue >= (long) actualValue;
        }
        return ((String) schemaValue).compareTo((String) actualValue) >= 0;
      }),

      Map.entry(ConstraintVocabulary.MOD, (schemaValue, actualValue ) -> {
        ArrayList<Integer> sv = (ArrayList<Integer>) schemaValue;
        int av = (int) actualValue;
        return av % sv.get(0) == sv.get(1);
      }));

}
