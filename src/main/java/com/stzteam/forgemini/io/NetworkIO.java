package com.stzteam.forgemini.io;

import edu.wpi.first.networktables.*;
import edu.wpi.first.util.struct.Struct;
import edu.wpi.first.wpilibj.util.Color;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NetworkTables Input/Output Engine.
 * <p>
 * Handles optimized communication with the Dashboard.
 * Now features <b>"Magic Struct"</b> support to automatically detect and publish 
 * complex types (like {@code Pose2d}) without manual configuration.
 * </p>
 */
public class NetworkIO {

    private static final NetworkTableInstance inst = NetworkTableInstance.getDefault();
    
    // Cache maps to avoid creating objects every cycle
    private static final ConcurrentHashMap<String, Publisher> publishers = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    
    // Struct Cache (To avoid using expensive reflection every cycle)
    private static final ConcurrentHashMap<Class<?>, Struct<?>> structCache = new ConcurrentHashMap<>();

    private NetworkIO() {}

    // ============================================================
    //  OUTPUT (SETTERS)
    // ============================================================

    /**
     * The "Magic" setter for complex objects (e.g., Pose2d, ChassisSpeeds).
     * <p>
     * You <b>NO LONGER</b> need to pass the {@code .struct} object manually. 
     * This method inspects the object at runtime, automatically finds its 
     * associated struct, and publishes it to the Dashboard.
     * </p>
     * @param table The table name.
     * @param key The value name.
     * @param value The object to publish (e.g., a Pose2d instance).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void set(String table, String key, Object value) {
        if (value == null) return;

        // If a primitive type slipped in here, redirect it to the fast overload (Safety)
        if (value instanceof Double) { set(table, key, (double) value); return; }
        if (value instanceof Boolean) { set(table, key, (boolean) value); return; }
        if (value instanceof String) { set(table, key, (String) value); return; }
        if (value instanceof Integer){ set(table, key, (int) value); return;}

        String path = table + "/" + key;

        if (value.getClass().isArray()) {
            handleArray(table, key, path, value);
            return;
        }
        
        // Find or create the publisher
        Publisher pub = publishers.computeIfAbsent(path, k -> {
            try {
          
                Struct struct = getStructForClass(value.getClass());
                if (struct != null) {
                    return inst.getTable(table).getStructTopic(key, struct).publish();
                } else {
                    System.err.println("[NetworkIO] Error: No .struct found for class " + value.getClass().getSimpleName());
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });

        // If the publisher was created successfully, send the data
        if (pub instanceof StructPublisher) {
            ((StructPublisher) pub).set(value);
        }
    }

    /**
     * Handles the logic for publishing arrays (Primitive Arrays & Struct Arrays).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void handleArray(String table, String key, String path, Object value) {
        
        // A. Primitive Arrays
        if (value instanceof double[]) {
            Publisher pub = publishers.computeIfAbsent(path, k -> inst.getTable(table).getDoubleArrayTopic(key).publish());
            ((DoubleArrayPublisher) pub).set((double[]) value);
            return;
        }
        if (value instanceof boolean[]) {
            Publisher pub = publishers.computeIfAbsent(path, k -> inst.getTable(table).getBooleanArrayTopic(key).publish());
            ((BooleanArrayPublisher) pub).set((boolean[]) value);
            return;
        }
        if (value instanceof String[]) {
            Publisher pub = publishers.computeIfAbsent(path, k -> inst.getTable(table).getStringArrayTopic(key).publish());
            ((StringArrayPublisher) pub).set((String[]) value);
            return;
        }

        if (value instanceof int[]) {
            Publisher pub = publishers.computeIfAbsent(path, k -> inst.getTable(table).getIntegerArrayTopic(key).publish());
            
            int[] intArray = (int[]) value;
            long[] longArray = new long[intArray.length];
            for (int i = 0; i < intArray.length; i++) {
                longArray[i] = intArray[i];
            }
            
            ((IntegerArrayPublisher) pub).set(longArray);
            return;
        }

        if (value instanceof float[]) {
            Publisher pub = publishers.computeIfAbsent(path, k -> inst.getTable(table).getFloatArrayTopic(key).publish());
            ((FloatArrayPublisher) pub).set((float[]) value);
            return;
        }

        // B. Struct Arrays (e.g. Pose2d[])
        Publisher pub = publishers.computeIfAbsent(path, k -> {
            try {
                // Get the type of the array elements (e.g., Pose2d.class)
                Class<?> componentType = value.getClass().getComponentType();
                Struct struct = getStructForClass(componentType);
                
                if (struct != null) {
                    // IMPORTANT: We use getStructArrayTopic here
                    return inst.getTable(table).getStructArrayTopic(key, struct).publish();
                } else {
                    System.err.println("[NetworkIO] Error: No .struct found for array type " + componentType.getSimpleName());
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });

        if (pub instanceof StructArrayPublisher) {
            // Cast to Object[] is safe here because we are in rawtypes context
            ((StructArrayPublisher) pub).set((Object[]) value);
        }
    }

    /**
     * Helper method to find the static 'struct' field using reflection and cache it.
     */
    private static Struct<?> getStructForClass(Class<?> clazz) {
        return structCache.computeIfAbsent(clazz, c -> {
            try {
                Field field = c.getField("struct");
                return (Struct<?>) field.get(null);
            } catch (Exception e) {
                return null;
            }
        });
    }

    // ============================================================
    //  SPECIFIC OVERLOADS (For maximum speed on primitives)
    // ============================================================

    /**
     * Publishes a double value.
     * <p>
     * Explicit overload for maximum performance (bypasses reflection).
     * </p>
     * @param table The table name.
     * @param key The key name.
     * @param value The value to publish.
     */
    public static void set(String table, String key, double value) {
        String path = table + "/" + key;
        Publisher pub = publishers.computeIfAbsent(path, k -> 
            inst.getTable(table).getDoubleTopic(key).publish()
        );
        ((DoublePublisher) pub).set(value);
    }

    /**
     * Publishes an int value.
     * <p>
     * Explicit overload for maximum performance (bypasses reflection).
     * </p>
     * @param table The table name.
     * @param key The key name.
     * @param value The value to publish.
     */
    public static void set(String table, String key, int value) {
        String path = table + "/" + key;
        Publisher pub = publishers.computeIfAbsent(path, k -> 
            inst.getTable(table).getIntegerTopic(key).publish()
        );
        ((IntegerPublisher) pub).set(value);
    }
    
    /**
     * Publishes a String value.
     * @param table The table name.
     * @param key The key name.
     * @param value The value to publish.
     */
    public static void set(String table, String key, String value) {
        String path = table + "/" + key;
        Publisher pub = publishers.computeIfAbsent(path, k -> 
            inst.getTable(table).getStringTopic(key).publish()
        );
        ((StringPublisher) pub).set(value);
    }

    /**
     * Publishes a boolean value.
     * @param table The table name.
     * @param key The key name.
     * @param value The value to publish.
     */
    public static void set(String table, String key, boolean value) {
        String path = table + "/" + key;
        Publisher pub = publishers.computeIfAbsent(path, k -> 
            inst.getTable(table).getBooleanTopic(key).publish()
        );
        ((BooleanPublisher) pub).set(value);
    }

    /**
     * Publishes a double array value.
     * <p>
     * Explicit overload for maximum performance (bypasses reflection).
     * </p>
     * @param table The table name.
     * @param key The key name.
     * @param value The value to publish.
     */
    public static void set(String table, String key, double[] value) {
        String path = table + "/" + key;
        Publisher pub = publishers.computeIfAbsent(path, k -> 
            inst.getTable(table).getDoubleArrayTopic(key).publish()
        );
        ((DoubleArrayPublisher) pub).set(value);
    }


    /**
     * Publishes a Color value (hexString).
     * <p>
     * Explicit overload for maximum performance (bypasses reflection).
     * </p>
     * @param table The table name.
     * @param key The key name.
     * @param value The value to publish.
     */
    public static void set(String table, String key, Color value){
        set(table, key, value.toHexString());
    }

    // ============================================================
    //  INPUT (GETTERS)
    // ============================================================

    /**
     * Retrieves a double from the Dashboard.
     * @param table The table name.
     * @param key The key name.
     * @param defaultValue The value to return if not found.
     * @return The value from NetworkTables.
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
     * @param defaultValue The value to return if not found.
     * @return The value from NetworkTables.
     */
    public static boolean get(String table, String key, boolean defaultValue) {
        String path = table + "/" + key;
        Subscriber sub = subscribers.computeIfAbsent(path, k -> 
            inst.getTable(table).getBooleanTopic(key).subscribe(defaultValue)
        );
        return ((BooleanSubscriber) sub).get();
    }

    /**
     * Retrieves a Color from the Dashboard.
     * @param table The table name.
     * @param key The key name.
     * @param defaultValue The value to return if not found.
     * @return The value from NetworkTables.
     */
    public static double[] get(String table, String key, double[] defaultValue) {
        String path = table + "/" + key;
        Subscriber sub = subscribers.computeIfAbsent(path, k -> 
            inst.getTable(table).getDoubleArrayTopic(key).subscribe(defaultValue)
        );
        return ((DoubleArraySubscriber) sub).get();
    }

    public static void processInputs(String rootTable, String subTable, Object inputs) {
        Class<?> clazz = inputs.getClass();

        String basePath = rootTable + "/" + subTable;
        
        Field[] fields = clazz.getFields();

        for (Field field : fields) {
            try {
                String key = field.getName();
                Object value = field.get(inputs);
                if (value == null) continue;
                set(basePath, key, value);

            } catch (IllegalAccessException e) {
                System.err.println("[NetworkIO] No se pudo leer el campo: " + field.getName());
            }
        }
    }
    

    // ============================================================
    //  UTILITIES
    // ============================================================
    
    /**
     * Closes all publishers and subscribers associated with a specific table.
     * @param tableName The name of the table to clean up.
     */
    public static void closeAll(String tableName) {
        String prefix = tableName + "/";
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