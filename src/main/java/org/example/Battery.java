package org.example;

public class Battery {

    // todo: consider adding charging and discharging efficiency

    private float maxCapacity;
    private float currentLoad;

    public Battery() {
        this(50000, 0);
    }

    public Battery(float maxCapacity, float currentLoad) {
        this.maxCapacity = maxCapacity;
        this.currentLoad = currentLoad;
    }

    public float charge(float inputLoad) {
        this.currentLoad += inputLoad;
        if (this.currentLoad > this.maxCapacity) this.currentLoad = this.maxCapacity;
        return this.currentLoad;
    }

    public float discharge(float outputLoad) {
        this.currentLoad -= outputLoad;
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
