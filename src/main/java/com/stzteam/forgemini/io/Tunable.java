package com.stzteam.forgemini.io;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be updated from NetworkTables/Dashboard (Input).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Tunable {
    /**
     * The name of the key in NetworkTables.
     * @return The key name. If empty, uses the field name.
     */
    String key() default "";
}