package com.stzteam.forgemini;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.livewindow.LiveWindow;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

import com.stzteam.forgemini.io.NetworkIO;

/**
 * <b>Optimizer</b>
 * <p>
 * A static utility class responsible for managing system resources to ensure maximum robot performance.
 * It handles memory management, disk hygiene, and performance monitoring.
 * </p>
 * * <h3>Key Responsibilities:</h3>
 * <ul>
 * <li><b>Garbage Collection (GC):</b> Triggers GC only when the robot is disabled and memory is critical, preventing mid-match lag spikes.</li>
 * <li><b>Loop Monitoring:</b> Tracks execution time and logs "Loop Overruns" if the code takes longer than 20ms.</li>
 * <li><b>Disk Hygiene:</b> Automatically deletes old .wpilog and .hres files to prevent disk saturation.</li>
 * <li><b>Telemetry Optimization:</b> Disables LiveWindow to save bandwidth and CPU cycles.</li>
 * </ul>
 */
public class Optimizer {

    // --- RAM CONFIGURATION ---
    /** Threshold ratio (0.0 to 1.0) of used RAM that triggers a GC event (only in disabled). */
    private static final double RAM_USAGE_THRESHOLD = 0.65; 
    /** Minimum seconds to wait between forced GC events to avoid CPU spamming. */
    private static final double GC_COOLDOWN = 5.0; 
    private static double lastGCTime = 0;

    // --- LOG CONFIGURATION ---
    private static String LOG_DIR = "/home/lvuser/logs"; 
    private static int MAX_LOG_FILES = 10; 
    
    private static final String table = "Optimizer";
    
    // --- METRICS ---
    private static double LOOP_OVERRUN_THRESHOLD = 0.02; // 20ms standard loop
    
    private Optimizer() {
        // Private constructor to prevent instantiation.
    } 
    
    /**
     * Initializes the Optimizer.
     * <p>
     * Call this method <b>ONCE</b> in your {@code Robot.robotInit()} method.
     * It disables LiveWindow telemetry and starts the background thread for log cleanup.
     * </p>
     */
    public static void init() {
        // 1. Kill LiveWindow: Saves CPU and Bandwidth
        LiveWindow.disableAllTelemetry();
        System.out.println("[Optimizer] LiveWindow Telemetry disabled.");
    
        // 2. Clean Disk: Run in a separate thread to avoid slowing down startup
        new Thread(Optimizer::cleanOldLogs).start();
    }
    
    /**
     * Sets the directory where log files are stored.
     * @param directory The absolute path to the logs folder (default: "/home/lvuser/logs").
     */
    public static void setLogDir(String directory){
        LOG_DIR = directory;
    }

    /**
     * Sets the maximum number of log files to keep. Older files will be deleted.
     * @param max The maximum number of files (default: 10).
     */
    public static void setMaxLogs(int max){
        MAX_LOG_FILES = max;
    }

    /**
     * Sets the time threshold (in seconds) for considering a loop as an "overrun".
     * @param loopSeconds The threshold (default: 0.02s).
     */
    public static void setLoopOverrunThreshold(double loopSeconds){
        LOOP_OVERRUN_THRESHOLD = loopSeconds;
    }

    /**
     * Updates the performance monitor.
     * <p>
     * Call this method at the end of {@code Robot.robotPeriodic()}.
     * </p>
     * * <h3>Usage Example:</h3>
     * <pre>
     * double startTime = Timer.getFPGATimestamp();
     * CommandScheduler.getInstance().run();
     * double executionTime = Timer.getFPGATimestamp() - startTime;
     * Optimizer.update(executionTime);
     * </pre>
     * * @param loopTimeSeconds The actual execution time of the current loop iteration (measured manually).
     * <b>DO NOT pass {@code TimedRobot.getPeriod()}.</b>
     */
    public static void update(double loopTimeSeconds) {
        
        // --- 1. PERFORMANCE MONITORING (Loop Time) ---
        NetworkIO.set(table, "Sys/LoopTime_ms", loopTimeSeconds * 1000);
        
        if (loopTimeSeconds > LOOP_OVERRUN_THRESHOLD) {
            // Read-Modify-Write the overrun counter
            double currentOverruns = NetworkIO.get(table, "Sys/LoopOverruns", 0.0);
            NetworkIO.set(table, "Sys/LoopOverruns", currentOverruns + 1);
        }

        // --- 2. MEMORY MANAGEMENT (Disabled Mode Only) ---
        if (DriverStation.isDisabled()) {
            manageMemoryStrategy();
        }

        // --- 3. RAM TELEMETRY ---
        double totalMem = Runtime.getRuntime().totalMemory();
        double freeMem = Runtime.getRuntime().freeMemory();
        double usedMem = totalMem - freeMem;
        double usedRatio = usedMem / totalMem;

        NetworkIO.set(table, "Sys/RAM_Usage_%", usedRatio * 100);
        NetworkIO.set(table, "Sys/RAM_Free_MB", freeMem / 1024.0 / 1024.0);
    }

    /**
     * Intelligent memory management strategy.
     * <p>
     * Checks if RAM usage is high (>65%) while the robot is disabled.
     * If so, and the cooldown has passed, it triggers a manual {@code System.gc()}.
     * This forces the "stop-the-world" cleanup to happen while idle, preventing it from happening during a match.
     * </p>
     */
    private static void manageMemoryStrategy() {
        double now = Timer.getFPGATimestamp();

        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        double usedRatio = (double) (totalMem - freeMem) / totalMem;

        if (usedRatio > RAM_USAGE_THRESHOLD && (now - lastGCTime > GC_COOLDOWN)) {
            System.out.println("[Optimizer] Maintenance: Cleaning RAM (Usage: " + (int)(usedRatio*100) + "%)...");
            System.gc(); 
            lastGCTime = now;
        }
    }
    
    /**
     * Janitor logic: Maintains disk hygiene by deleting old logs.
     * <p>
     * Scans the log directory for .wpilog and .hres files. If the count exceeds {@code MAX_LOG_FILES},
     * it deletes the oldest files based on their last modified timestamp.
     * </p>
     */
    private static void cleanOldLogs() {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists() || !dir.isDirectory()) return;

            // Filter for WPILib (.wpilog) and Phoenix Flight Recorder (.hres) logs
            File[] files = dir.listFiles((d, name) -> name.endsWith(".wpilog") || name.endsWith(".hres"));
            
            if (files != null && files.length > MAX_LOG_FILES) {
                // Sort by oldest first
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));

                int filesToDelete = files.length - MAX_LOG_FILES;
                for (int i = 0; i < filesToDelete; i++) {
                    if (files[i].delete()) {
                        System.out.println("[Optimizer] Deleted old log: " + files[i].getName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Optimizer] Error cleaning logs: " + e.getMessage());
        }
    }
}