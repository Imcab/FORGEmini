package com.stzteam.forgemini.io;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be published to NetworkTables/Dashboard.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Signal {
    /**
     * The name of the key in NetworkTables.
     * @return The key name. If empty, uses the method name.
     */
    String key() default "";

    /**
     * If true, only updates the value when it changes significantly.
     * @return True to enable change filtering.
     */
    boolean onChange() default false;

    /**
     * Updates the value only once every N cycles (Slow Scale).
     * @return The number of cycles to skip (1 = every cycle).
     */
    int slowScale() default 1;
}