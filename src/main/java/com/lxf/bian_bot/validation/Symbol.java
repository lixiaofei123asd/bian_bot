package com.lxf.bian_bot.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {SymbolValidator.class})
public @interface Symbol {

    String message() default "参数不正确！";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
