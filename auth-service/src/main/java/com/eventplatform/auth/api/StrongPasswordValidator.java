package com.eventplatform.auth.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < 12 || byteLength > 72) {
            return false;
        }
        boolean hasLetter = value.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = value.codePoints().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }
}
