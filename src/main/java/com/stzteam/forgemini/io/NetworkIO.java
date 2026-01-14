package com.stzteam.forgemini.io;

import edu.wpi.first.networktables.*;
import edu.wpi.first.util.struct.Struct;

import java.util.concurrent.ConcurrentHashMap;

/**
 * NetworkTables Input/Output Engine.
 * <p>
 * Handles optimized communication with the Dashboard by caching Publishers and Subscribers.
 * This prevents the expensive creation of NT objects during every robot cycle.
 * </p>
 */
public class NetworkIO {

    private static final NetworkTableInstance inst = NetworkTableInstance.getDefault();
    
    // Cache maps to avoid creating objects every cycle (Performance Optimization)
    private static final ConcurrentHashMap<String, Publisher> publishers = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Subscriber> subscribers = new ConcurrentHashMap<>();

    // Private constructor to prevent instantiation
    private NetworkIO() {}

    // ============================================================
    //  OUTPUT (SETTERS) - Sends data to Dashboard
    // ============================================================

    /**
     * Publishes complex objects (Pose2d, ChassisSpeeds, SwerveStates) using Structs.
     * <p>
     * This method automatically handles the serialization of the object using WPILib's Struct API.
     * </p>
     * @param <T> The type of the object.
     * @param table The main table name (e.g., "DriveTrain").
     * @param key The specific value name (e.g., "Pose").
     * @param value The object to publish.
     * @param struct The struct definition for serialization.
     */
    @SuppressWarnings("unchecked")
    public static <T> void set(String table, String key, T value, Struct<T> struct) {
        String path = table + "/" + key;
        Publisher pub = publishers.computeIfAbsent(path, k -> 
            inst.getTable(table).getStructTopic(key, struct).publish()
        );
        try { ((StructPublisher<T>) pub).set(value); } catch (Exception e) {}
    }

    /**
     * Publishes a double value to NetworkTables.
     * @param table The table name.
     * @param key The key name.
     * @param value The value to send.
     */
    public static void set(String table, String key, double value) {
        String path = table + "/" + key;
        Publisher pub = publishers.computeIfAbsent(path, k -> 
            inst.getTable(table).getDoubleTopic(key).publish()
        );
        ((DoublePublisher) pub).set(value);
    }
    
    /**
     * Publishes a String value to NetworkTables.
     * @param table The table name.
     * @param key The key name.
     * @param value The string to send.
     */
    public static void set(String table, String key, String value) {
        String path = table + "/" + key;
        Publisher pub = publishers.computeIfAbsent(path, k -> 
            inst.getTable(table).getStringTopic(key).publish()
        );
        ((StringPublisher) pub).set(value);
    }

    /**
     * Publishes a boolean value to NetworkTables.
     * @param table The table name.
     * @param key The key name.
     * @param value The boolean to send.
     */
    public static void set(String table, String key, boolean value) {
        String path = table + "/" + key;
        Publisher pub = publishers.computeIfAbsent(path, k -> 
            inst.getTable(table).getBooleanTopic(key).publish()
        );
        ((BooleanPublisher) pub).set(value);
    }

    // ============================================================
    //  INPUT (GETTERS) - Reads data from Dashboard
    // ============================================================

    /**
     * Retrieves a double from the Dashboard.
     * <p>
     * Useful for tunable PIDs or reading configuration values.
     * </p>
     * @param table The table name.
     * @param key The key name.
     * @param defaultValue The value to return if the key does not exist.
     * @return The current value from the network, or the default value.
     */
    public static double get(String table, String key, double defaultValue) {
        String path = table + "/" + key;
        Subscriber sub = subscribers.computeIfAbsent(path, k -> 
            inst.getTable(table).getDoubleTopic(key).subscribe(defaultValue)
        );
        return ((DoubleSubscriber) sub).get();
    }
    
    /**
     * Retrieves a boolean from the Dashboard.
     * @param table The table name.
     * @param key The key name.
     * @param defaultValue The value to return if the key does not exist.
     * @return The current value from the network, or the default value.
     */
    public static boolean get(String table, String key, boolean defaultValue) {
        String path = table + "/" + key;
        Subscriber sub = subscribers.computeIfAbsent(path, k -> 
            inst.getTable(table).getBooleanTopic(key).subscribe(defaultValue)
        );
        return ((BooleanSubscriber) sub).get();
    }

    // ============================================================
    //  UTILITIES
    // ============================================================
    
    /**
     * Closes all publishers and subscribers associated with a specific table.
     * <p>
     * Call this when a subsystem is being destroyed or reset to prevent memory leaks.
     * </p>
     * @param tableName The name of the table to clean up.
     */
    public static void closeAll(String tableName) {
        String prefix = tableName + "/";
        // Clean publishers and subscribers for that specific table
        publishers.entrySet().removeIf(e -> checkAndClose(e, prefix));
        subscribers.entrySet().removeIf(e -> checkAndClose(e, prefix));
    }

    private static boolean checkAndClose(java.util.Map.Entry<String, ? extends AutoCloseable> entry, String prefix) {
        if (entry.getKey().startsWith(prefix)) {
            try { entry.getValue().close(); } catch (Exception e) {}
            return true;
        }
        return false;
    }
}