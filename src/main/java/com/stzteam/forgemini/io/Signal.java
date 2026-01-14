package com.stzteam.forgemini.io;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to automatically publish its return value to NetworkTables.
 * <p>
 * The {@link IOSubsystem} detects this annotation and creates an optimized task 
 * to send the data to the Dashboard. This allows for easy telemetry without 
 * cluttering the periodic logic.
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Signal {
    /**
     * The key name to be used in NetworkTables.
     */
    String key();

    /**
     * If set to {@code true}, data will only be sent to the network when the value changes.
     * <p>
     * This is ideal for optimizing bandwidth with booleans, state machines (Enums), 
     * or configuration values that rarely change.
     * </p>
     * Default: {@code false} (publishes continuously).
     */
    boolean onChange() default false;

    /**
     * Defines the update frequency in terms of robot cycles (downsampling).
     * <ul>
     * <li>1 = Every cycle (20ms) - High Priority (e.g., Odometry, Gyro).</li>
     * <li>2 = Every 2 cycles (40ms).</li>
     * <li>10 = Every 10 cycles (200ms) - Low Priority (e.g., Battery, Temperatures).</li>
     * </ul>
     * Default: 1.
     */
    int slowScale() default 1;
}