package org.example;

public class Battery {

    /*
    Battery model that includes efficiency and
    linear charging/discharging rate limits with respect to battery capacity
    refer to C/L/C model in following reference for details:
    "Tractable lithium-ion storage models for optimizing energy systems."
    */

    // todo: consider adding charging and discharging limits (max amount of charge/discharge the battery can take each time step)

    private float maxCapacity;
    private float currentLoad;

    private final double chargingEfficiency;
    private final double dischargingEfficiency;

    public Battery() {
        this(50000, 0);
    }

    public Battery(float maxCapacity, float currentLoad) {
        this.maxCapacity = maxCapacity;
        this.currentLoad = currentLoad;

        // defaults for lithium NMC cell
        this.chargingEfficiency = 0.98;
        this.dischargingEfficiency = 1.05;
    }

    public float charge(float inputLoad) {
        this.currentLoad += (this.chargingEfficiency * inputLoad);
        if (this.currentLoad > this.maxCapacity) this.currentLoad = this.maxCapacity;

        // todo: return currentLoad? why not the charge value
        return this.currentLoad;
    }

    public float discharge(float outputLoad) {
        this.currentLoad -= (this.dischargingEfficiency * outputLoad);
        if (this.currentLoad < 0) {
            float lackingAmount = this.currentLoad;
            this.currentLoad = 0;
            return outputLoad + lackingAmount;
        }
        return outputLoad;
    }

    public boolean isFull() {
        return this.maxCapacity == this.currentLoad;
    }

    public float getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(float maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public float getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(float currentLoad) {
        this.currentLoad = currentLoad;
    }
}
