package fr.recia.mce.api.escomceapi.services.exception;

public class PersonneNotFoundException extends RuntimeException {
    public PersonneNotFoundException(String message) {
        super(message);
    }
}
