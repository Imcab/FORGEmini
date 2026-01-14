package com.stzteam.forgemini.io;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a variable (field) as tunable from the Dashboard.
 * <p>
 * {@link IOSubsystem} will automatically keep this variable updated.
 * If you change the value in the Dashboard (e.g., via a slider or toggle), 
 * the variable's value on the robot will update in real-time.
 * </p>
 * Supported types: {@code double} and {@code boolean}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD) // Important: FIELD, not METHOD
public @interface Tunable {
    /**
     * The key name to be used in the Dashboard.
     * If left empty, the field's variable name will be used by default.
     */
    String key() default "";
}