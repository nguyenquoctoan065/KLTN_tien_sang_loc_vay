package vn.kltn.tiensanglocvay.application;

/** Schema CrossCheckWarning; lưu ở cột jsonb loan_application.warnings. {@code severity}: INFO, WARNING, BLOCKING. */
public record CrossCheckWarning(String field, String declared, String verified, String message, String severity) {

    public static final String INFO = "INFO";
    public static final String WARNING = "WARNING";
    public static final String BLOCKING = "BLOCKING";

    public boolean blocking() {
        return BLOCKING.equals(severity);
    }
}
