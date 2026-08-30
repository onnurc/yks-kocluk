package com.ykskocluk.demo.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrictEmailValidator implements ConstraintValidator<StrictEmail, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || EmailAddresses.isValid(value);
    }
}
