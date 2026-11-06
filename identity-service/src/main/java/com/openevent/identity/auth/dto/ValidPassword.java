package com.openevent.identity.auth.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Mật khẩu 8-72 byte. BCrypt chỉ xử lý 72 byte đầu, và tiếng Việt có dấu chiếm nhiều byte mỗi ký tự. */
@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "mật khẩu phải có từ 8 ký tự và không quá 72 byte";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
