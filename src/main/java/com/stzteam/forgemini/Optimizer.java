package com.stzteam.forgemini;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.livewindow.LiveWindow;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * System resource manager.
 * <p>
 * This utility class is responsible for maintaining robot health by:
 * 1. Managing RAM (Triggering GC) when the robot is idle.
 * 2. Cleaning old log files to prevent disk saturation.
 * 3. Disabling unnecessary telemetry (LiveWindow) to save CPU and bandwidth.
 * </p>
 */
public class Optimizer {

    // RAM Configuration
    private static final double MEMORY_THRESHOLD = 0.20; 
    private static int loopCounter = 0;

    // Log Configuration
    private static final String LOG_DIR = "/home/lvuser/logs"; // Standard WPILib path
    private static final int MAX_LOG_FILES = 10; // Keep only the last 10 matches (adjustable)

    private Optimizer() {} 

    /**
     * Call this method ONCE in {@code robotInit()}.
     * <p>
     * Configures the environment for maximum performance and disk space efficiency.
     * It disables LiveWindow and starts the background thread for log cleanup.
     * </p>
     */
    public static void init() {
        // 1. Kill LiveWindow: It is rarely used in competition and consumes resources.
        LiveWindow.disableAllTelemetry();
        System.out.println("[Optimizer] LiveWindow Telemetry disabled.");

        // 2. Clean Disk: Delete old logs in a separate thread to avoid slowing down startup.
        new Thread(Optimizer::cleanOldLogs).start();
    }

    /**
     * Call this method in {@code robotPeriodic()}.
     * <p>
     * Monitors RAM health. If the robot is disabled and memory is low, 
     * it forces a Garbage Collection (GC) to prevent lag spikes during the match.
     * </p>
     */
    public static void update() {
        if (DriverStation.isDisabled()) {
            loopCounter++;
            if (loopCounter > 100) { // Every 2 seconds (approx)
                loopCounter = 0;
                
                long totalMem = Runtime.getRuntime().totalMemory();
                long freeMem = Runtime.getRuntime().freeMemory();
                double freeRatio = (double) freeMem / totalMem;

                if (freeRatio < MEMORY_THRESHOLD) {
                    System.out.println("[Optimizer] Critical Memory (" + (int)(freeRatio*100) + "%). Running GC...");
                    System.gc();
                }
            }
        } else {
            loopCounter = 0;
        }
    }
    
    /**
     * Janitor logic: Keeps only the most recent log files.
     * Running this ensures the roboRIO never runs out of disk space.
     */
    private static void cleanOldLogs() {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists() || !dir.isDirectory()) return;

            // List .wpilog files
            File[] files = dir.listFiles((d, name) -> name.endsWith(".wpilog"));
            
            if (files != null && files.length > MAX_LOG_FILES) {
                // Sort by last modified (oldest first)
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));

                // Delete the excess files (the oldest ones)
                int filesToDelete = files.length - MAX_LOG_FILES;
                for (int i = 0; i < filesToDelete; i++) {
                    if (files[i].delete()) {
                        System.out.println("[Optimizer] Log deleted to free up space: " + files[i].getName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Optimizer] Error cleaning logs: " + e.getMessage());
        }
    }

    /**
     * Gets the current battery voltage.
     * @return The battery voltage in volts.
     */
    public static double getVoltage() {
        return RobotController.getBatteryVoltage();
    }
}