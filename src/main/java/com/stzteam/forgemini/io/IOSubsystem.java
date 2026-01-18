package com.stzteam.forgemini.io;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.util.Color;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The high-performance base class for all ForgeMini subsystems.
 * <p>
 * This class abstracts the complexity of NetworkTables, providing a seamless bridge
 * between robot logic and the Dashboard. It uses advanced Java reflection during initialization
 * to compile optimized tasks, ensuring <b>zero CPU overhead</b> during the match.
 * </p>
 * <h2>Features:</h2>
 * <ul>
 * <li><b>{@link Signal} (Output):</b> Automatically publishes methods to the Dashboard.</li>
 * <li><b>{@link Tunable} (Input):</b> Injects Dashboard values directly into fields.</li>
 * <li><b>Lazy Initialization:</b> Waits for the first periodic cycle to ensure fields are set.</li>
 * </ul>
 */
public abstract class IOSubsystem extends SubsystemBase {

    /**
     * The name of the subsystem
     */
    public final String tableName;
    private boolean isInitialized = false;
    
    // Pre-compiled tasks (Runnables) to avoid reflection during runtime
    private final List<Runnable> signalTasks = new ArrayList<>();
    private final List<Runnable> tunableTasks = new ArrayList<>();

    /**
     * Creates a new IOSubsystem.
     * <p>
     * Note: NetworkTables registration is deferred until the first {@link #periodic()} call
     * (Lazy Initialization) to ensure child class fields are fully initialized.
     * </p>
     * @param tableName The name of the table in NetworkTables (e.g., "Shooter", "DriveTrain").
     */
    public IOSubsystem(String tableName) {
        this.tableName = tableName;
    }

    // ============================================================
    //  SECTION 1: SIGNALS (Output)
    // ============================================================

    /**
     * Scans methods for @Signal annotation and compiles optimized publishing tasks.
     */
    private void registerSignals() {
        for (Method method : this.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Signal.class)) continue;

            Signal annotation = method.getAnnotation(Signal.class);
            String key = annotation.key().isEmpty() ? method.getName() : annotation.key();
            method.setAccessible(true);

            try {
                createOptimizedSignalTask(key, method, annotation);
            } catch (Exception e) {
                System.err.println("[IOSubsystem] Error registering Signal '" + key + "': " + e.getMessage());
            }
        }
    }

    // ============================================================
    //  OPTIMIZED SECTION: SIGNAL LOGIC WITH FILTERS
    // ============================================================

    private void createOptimizedSignalTask(String key, Method method, Signal config) {
        Class<?> type = method.getReturnType();
        boolean checkChange = config.onChange();
        int period = Math.max(1, config.slowScale()); 

        final int[] cycleCounter = {0}; 
        
        if (type == double.class || type == Double.class) {
            final double[] lastVal = { Double.NaN };
            signalTasks.add(() -> {
                cycleCounter[0]++;
                if (cycleCounter[0] < period) return; 
                cycleCounter[0] = 0; 
                try {
                    double current = ((Number) method.invoke(this)).doubleValue();
                    if (!checkChange || Math.abs(current - lastVal[0]) > 1e-5) { 
                        NetworkIO.set(tableName, key, current);
                        lastVal[0] = current;
                    }
                } catch (Exception e) {}
            });
        }
        else if (type == boolean.class || type == Boolean.class) {
            final boolean[] lastVal = { false };
            final boolean[] isFirstRun = { true };
            signalTasks.add(() -> {
                cycleCounter[0]++;
                if (cycleCounter[0] < period) return;
                cycleCounter[0] = 0;
                try {
                    boolean current = (boolean) method.invoke(this);
                    if (!checkChange || isFirstRun[0] || current != lastVal[0]) {
                        NetworkIO.set(tableName, key, current);
                        lastVal[0] = current;
                        isFirstRun[0] = false;
                    }
                } catch (Exception e) {}
            });
        }
        else if (type == String.class) {
            final String[] lastVal = { null };
            signalTasks.add(() -> {
                cycleCounter[0]++;
                if (cycleCounter[0] < period) return;
                cycleCounter[0] = 0;
                try {
                    String current = (String) method.invoke(this);
                    if (!checkChange || lastVal[0] == null || !lastVal[0].equals(current)) {
                        NetworkIO.set(tableName, key, current);
                        lastVal[0] = current;
                    }
                } catch (Exception e) {}
            });
        }
        else if (isStructSupported(type)) {
             createStructTask(key, method, type, period, checkChange);
        }
    }
    
    private void createStructTask(String key, Method method, Class<?> type, int period, boolean checkChange) {
        try {
            final int[] cycleCounter = {0};
            final Object[] lastVal = { null }; 

            signalTasks.add(() -> {
                cycleCounter[0]++;
                if (cycleCounter[0] < period) return;
                cycleCounter[0] = 0;
                try { 
                    Object value = method.invoke(this);
                    if (value != null) {
                        if (!checkChange || lastVal[0] == null || !lastVal[0].equals(value)) {
                            // Delegates to magic NetworkIO to auto-resolve structs
                            NetworkIO.set(tableName, key, value);
                            if(checkChange) lastVal[0] = value;
                        }
                    }
                } catch (Exception e) {}
            });
        } catch (Exception e) {}
    }

    // ============================================================
    //  SECTION 2: TUNABLES (Input)
    // ============================================================

    /**
     * Scans fields for @Tunable annotation and configures bi-directional syncing.
     */
    private void registerTunables() {
        NetworkTable table = NetworkTableInstance.getDefault().getTable(tableName);
        for (Field field : this.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(Tunable.class)) continue;

            Tunable annotation = field.getAnnotation(Tunable.class);
            String key = annotation.key().isEmpty() ? field.getName() : annotation.key();
            field.setAccessible(true);
            try {
                if (field.getType() == double.class) {
                    setupDoubleTunable(table, key, field);
                } else if (field.getType() == boolean.class) {
                    setupBooleanTunable(table, key, field);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void setupDoubleTunable(NetworkTable table, String key, Field field) throws IllegalAccessException {
        // Read the actual initialized value from the subclass
        double initialValue = field.getDouble(this);
        var topic = table.getDoubleTopic(key);
        boolean exists = topic.exists();
        DoublePublisher pub = topic.publish();
        DoubleSubscriber sub = topic.subscribe(initialValue);

        if (!exists) {
            pub.set(initialValue);
        } else {
            // Update local field immediately if value exists in NT
            field.setDouble(this, sub.get());
        }

        final double[] lastValue = { sub.get() };
        tunableTasks.add(() -> {
            double currentNT = sub.get();
            if (currentNT != lastValue[0]) {
                try {
                    field.setDouble(this, currentNT);
                    lastValue[0] = currentNT;
                } catch (Exception e) {}
            }
        });
    }

    private void setupBooleanTunable(NetworkTable table, String key, Field field) throws IllegalAccessException {
        boolean initialValue = field.getBoolean(this);
        var topic = table.getBooleanTopic(key);
        boolean exists = topic.exists();
        BooleanPublisher pub = topic.publish();
        BooleanSubscriber sub = topic.subscribe(initialValue);

        if (!exists) pub.set(initialValue);
        else field.setBoolean(this, sub.get());

        final boolean[] lastValue = { sub.get() };
        tunableTasks.add(() -> {
            boolean currentNT = sub.get();
            if (currentNT != lastValue[0]) {
                try {
                    field.setBoolean(this, currentNT);
                    lastValue[0] = currentNT;
                } catch (Exception e) {}
            }
        });
    }

    private boolean isStructSupported(Class<?> type) {
        try { return type.getField("struct") != null; } catch (Exception e) { return false; }
    }

    @Override
    public void periodic() {
        // Lazy Load: Ensures subclass fields are initialized before registration
        if (!isInitialized) {
            registerSignals();
            registerTunables();
            isInitialized = true;
        }

        // 1. Signals (Send Data)
        for (int i = 0; i < signalTasks.size(); i++) signalTasks.get(i).run();

        // 2. Tunables (Receive Data)
        for (int i = 0; i < tunableTasks.size(); i++) tunableTasks.get(i).run();

        // 3. User Logic
        periodicLogic();
    }

    /**
     * The main logic loop for the subsystem.
     * <p>
     * Override this method instead of {@code periodic()}. It runs every 20ms
     * (or the robot loop time) after IO tasks have completed.
     * </p>
     */
    public abstract void periodicLogic();

    // ============================================================
    //  OUTPUT (SETTERS)
    // ============================================================

    /**
     * Publishes a double value.
     * <p>
     * Explicit overload for maximum performance (bypasses reflection).
     * </p>
     * @param key The key name.
     * @param value The value to publish.
     */
    public void setEntry(String key, double value){
        NetworkIO.set(tableName, key, value);
    }

    /**
     * Publishes a boolean value.
     * @param key The key name.
     * @param value The value to publish.
     */
    public void setEntry(String key, boolean value){
        NetworkIO.set(tableName, key, value);
    }

    /**
     * Publishes a String value.
     * @param key The key name.
     * @param value The value to publish.
     */
    public void setEntry(String key, String value){
        NetworkIO.set(tableName, key, value);
    }

    /**
     * Publishes a Color value (hexString).
     * <p>
     * Explicit overload for maximum performance (bypasses reflection).
     * </p>
     * @param key The key name.
     * @param value The value to publish.
     */
    public void setEntry(String key, Color value){
        NetworkIO.set(tableName, key, value);
    }

    /**
     * Publishes a double array value.
     * <p>
     * Explicit overload for maximum performance (bypasses reflection).
     * </p>
     * @param key The key name.
     * @param value The value to publish.
     */
    public void setEntry(String key, double[] value){
        NetworkIO.set(tableName, key, value);
    }

    /**
     * The "Magic" setter for complex objects (e.g., Pose2d, ChassisSpeeds).
     * <p>
     * You <b>NO LONGER</b> need to pass the {@code .struct} object manually. 
     * This method inspects the object at runtime, automatically finds its 
     * associated struct, and publishes it to the Dashboard.
     * </p>
     * @param key The value name.
     * @param value The object to publish (e.g., a Pose2d instance).
     */
    public void setEntry(String key, Object value){
        NetworkIO.set(tableName, key, value);
    }


    // ============================================================
    //  INPUT (GETTERS)
    // ============================================================

    /**
     * Retrieves a double from the Dashboard.
     * @param key The key name.
     * @param defaultValue The value to return if not found.
     * @return The value from NetworkTables.
     */
    public void getEntry(String key, double defaultValue){
        NetworkIO.get(tableName, key, defaultValue);
    }

    /**
     * Retrieves a double from the Dashboard.
     * @param table The table name.
     * @param defaultValue The value to return if not found.
     * @return The value from NetworkTables.
     */
    public void getEntry(String key, boolean defaultValue){
        NetworkIO.get(tableName, key, defaultValue);
    }

    /**
     * Retrieves a double from the Dashboard.
     * @param table The table name.
     * @param defaultValue The value to return if not found.
     * @return The value from NetworkTables.
     */
    public void getEntry(String key, double[] defaultValue){
        NetworkIO.get(tableName, key, defaultValue);
    }

    /**
     * Closes all NetworkTable publishers and subscribers associated with this subsystem.
     */
    public void close() {
        NetworkIO.closeAll(tableName);
    }
}