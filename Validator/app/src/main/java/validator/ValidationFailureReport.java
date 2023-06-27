package validator;

import java.util.ArrayList;

public class ValidationFailureReport {



  private final String failingAncestor;
  private final String failingAttribute;
  private final String failingConstraint;
  private final String actualValue;
  private final String actualLabel;
  private final long id;

  private final String toString;

  public ValidationFailureReport(String actualLabel, String failingAncestor, String failingAttribute,
      String failingConstraint, String actualValue, long id) {
    this.failingAncestor = failingAncestor;
    this.failingAttribute = failingAttribute;
    this.failingConstraint = failingConstraint;
    this.actualValue = actualValue;
    this.actualLabel = actualLabel;
    this.id = id;
    this.toString = "";
  }

  public ValidationFailureReport(String toString) {
    this.toString = toString;
    this.failingAncestor = null;
    this.failingAttribute = null;
    this.failingConstraint = null;
    this.actualValue = null;
    this.actualLabel = null;
    this.id = 0;
  }

  public String getFailingAncestor() {
    return failingAncestor;
  }

  public String getFailingAttribute() {
    return failingAttribute;
  }

  public String getFailingConstraint() {
    return failingConstraint;
  }

  public String getActualValue() {
    return actualValue;
  }

  @Override
  public String toString(){
    if (!toString.equals("")){
      return toString;
    }
    String init = String.format("VALIDATION FAILURE on Node with id '%d' and Label '%s'.", id,
        actualLabel);
    if (!failingAncestor.equals(actualLabel)){
      init+= String.format("\n\tThis Node failed because it is a descendant of Label '%s'",
          failingAncestor);
    }
    init+= String.format("\n\tThis Node failed on attribute '%s' with constraint '%s'.",
        failingAttribute, failingConstraint);
    init += String.format("\n\tThis Node has value '%s' for the property '%s'", actualValue,
        failingAttribute);
    return init;
  }

  public static ValidationFailureReport uniqueValidationError(String attribute, String value, String label, long count){
    String str = "\nVALIDATION FAILURE there is a unique constraint on attribute '"
        + attribute + "' for label 'label' "+label+" but there were " + count
        + " Node(s) found with value '" + value+"'";
    return new ValidationFailureReport(str);
  }

}
