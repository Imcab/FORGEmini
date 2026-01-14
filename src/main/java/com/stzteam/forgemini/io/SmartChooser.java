package com.stzteam.forgemini.io;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * A smart wrapper for {@link SendableChooser}.
 * <p>
 * This class adds a Fluent API (method chaining), null validation, and 
 * resolves the common issue of retrieving the display name of the selected option.
 * </p>
 * @param <T> The type of object the chooser will store (e.g., Command, String).
 */
public class SmartChooser<T> {

    private final SendableChooser<T> chooser;
    private final String key;
    
    // Aux map to remember names (Fix for getSelectedName)
    private final Map<T, String> optionNames = new HashMap<>();
    
    private boolean hasDefault = false;
    private T defaultValue;

    /**
     * Creates a new SmartChooser.
     * @param key The name (label) that will appear on the Dashboard.
     */
    public SmartChooser(String key) {
        this.key = Objects.requireNonNull(key, "Key cannot be null");
        this.chooser = new SendableChooser<>();
    }

    /**
     * Sets the default option.
     * @param name The name visible on the Dashboard.
     * @param value The associated object.
     * @return This instance (for method chaining).
     */
    public SmartChooser<T> setDefault(String name, T value) {
        hasDefault = true;
        this.defaultValue = Objects.requireNonNull(value, "Default value cannot be null");
        
        chooser.setDefaultOption(name, defaultValue);
        optionNames.put(value, name); // Save name for future lookup
        
        return this;
    }

    /**
     * Adds an extra option.
     * @param name The name visible on the Dashboard.
     * @param value The associated object.
     * @return This instance (for method chaining).
     */
    public SmartChooser<T> add(String name, T value) {
        if (!hasDefault) {
            System.err.println("[SmartChooser] WARNING: Adding options to '" + key + "' before setting Default. This may cause visual errors.");
        }

        chooser.addOption(name, Objects.requireNonNull(value, "Option value cannot be null"));
        optionNames.put(value, name); // Save name
        
        return this;
    }

    /**
     * Publishes the chooser to the SmartDashboard.
     * <p>
     * Note: SendableChoosers function best when placed at the root of the SmartDashboard.
     * </p>
     */
    public void publish() {
        SmartDashboard.putData(key, chooser);
    }

    /**
     * Gets the currently selected object.
     * @return The selected object.
     */
    public T get() {
        return chooser.getSelected();
    }

    /**
     * Gets the NAME (String) of the selected option.
     * <p>
     * Unlike standard methods, this returns the user-friendly name (e.g., "Right Auto") 
     * instead of the object's string representation (e.g., "Command@12345").
     * </p>
     * @return The display name of the selected option, or "No Selection" if null.
     */
    public String getSelectedName() {
        T selected = get();
        if (selected == null) return "No Selection";
        return optionNames.getOrDefault(selected, selected.toString());
    }

    /**
     * Gets the underlying SendableChooser object.
     * <p>
     * Useful for advanced manipulation or raw access.
     * </p>
     * @return The original SendableChooser instance.
     */
    public SendableChooser<T> getSendable() {
        return chooser;
    }
}