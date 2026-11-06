package com.openevent.identity.auth.dto;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_CHARS = 8;
    private static final int MAX_BYTES = 72;

    /** Dùng được cả ngoài Bean Validation, ví dụ khi tạo admin lúc khởi động. */
    public static boolean isValid(String password) {
        return password != null
                && password.codePointCount(0, password.length()) >= MIN_CHARS
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        return isValid(password);
    }
}
