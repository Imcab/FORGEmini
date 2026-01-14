package com.stzteam.forgemini.io;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.util.struct.Struct;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The optimized base class for ForgeMini subsystems.
 * <p>
 * Automatically handles data flow between the robot and the Dashboard using 
 * {@link Signal} (Output) and {@link Tunable} (Input/Adjustment) annotations.
 * This class abstracts away the complexity of NetworkTables, providing a clean 
 * and efficient API for subsystem development.
 * </p>
 */
public abstract class IOSubsystem extends SubsystemBase {

    private final String tableName;
    private boolean isInitialized = false;
    
    // Pre-compiled tasks (Runnables)
    private final List<Runnable> signalTasks = new ArrayList<>();
    private final List<Runnable> tunableTasks = new ArrayList<>();

    /**
     * Creates a new IOSubsystem.
     * @param tableName The name of the table in NetworkTables (e.g., "Shooter", "DriveTrain").
     */
    public IOSubsystem(String tableName) {
        this.tableName = tableName;
    }

    // ============================================================
    //  SECTION 1: SIGNALS (Output)
    // ============================================================

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
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void createStructTask(String key, Method method, Class<?> type, int period, boolean checkChange) {
        try {
            Struct<?> structObj = (Struct<?>) type.getField("struct").get(null);
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
                            NetworkIO.set(tableName, key, value, (Struct) structObj);
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
        // AHORA SÍ leemos el valor real, porque el constructor de Sub ya terminó
        double initialValue = field.getDouble(this);
        var topic = table.getDoubleTopic(key);
        boolean exists = topic.exists();
        DoublePublisher pub = topic.publish();
        DoubleSubscriber sub = topic.subscribe(initialValue);

        if (!exists) {
            pub.set(initialValue);
        } else {
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
        // Lazy Load
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
     * Define the periodic logic of your subsystem here.
     */
    public abstract void periodicLogic();
    
    public void close() {
        NetworkIO.closeAll(tableName);
    }
}