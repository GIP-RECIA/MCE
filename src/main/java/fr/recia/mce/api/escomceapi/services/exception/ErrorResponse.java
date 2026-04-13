package fr.recia.mce.api.escomceapi.services.exception;

import java.util.List;

public class ErrorResponse {

    private final String code;
    private final String message;
    private final List<String> details;

    public ErrorResponse(String code, String message, List<String> details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public List<String> getDetails() { return details; }
}