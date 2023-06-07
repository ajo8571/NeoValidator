package validator;

public class validationReport {
  private final ValidationStatus status;
  private final ValidationFailureReport report;

  public validationReport(ValidationStatus status, ValidationFailureReport report) {
    this.status = status;
    this.report = report;
  }

  public ValidationStatus getStatus() {
    return status;
  }

  public ValidationFailureReport getReport() {
    return report;
  }


}
