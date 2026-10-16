package com.rentatools.shared.domain;

/**
 * Excepción lanzada cuando se infringe una regla o invariante de negocio.
 */
public class BusinessRuleViolation extends DomainException {

    public BusinessRuleViolation(String code, String message) {
        super(code, message);
    }

    public BusinessRuleViolation(String message) {
        super("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleViolation(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
