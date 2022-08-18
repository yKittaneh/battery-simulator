package org.example;

public class Battery {

    // todo: consider adding charging and discharging efficiency

    private long maxCapacity;
    private long currentLoad;

    public Battery() {
        this(100L, 0L);
    }

    public Battery(long maxCapacity, long currentLoad) {
        this.maxCapacity = maxCapacity;
        this.currentLoad = currentLoad;
    }

    public long charge(long inputLoad) {
        this.currentLoad += inputLoad;
        if (this.currentLoad > this.maxCapacity) this.currentLoad = this.maxCapacity;
        return this.currentLoad;
    }

    public long discharge(long outputLoad) {
        this.currentLoad -= outputLoad;
        if (this.currentLoad < 0) {
            long lackingAmount = this.currentLoad;
            this.currentLoad = 0;
            return outputLoad + lackingAmount;
        }
        return outputLoad;
    }

    public boolean isFull() {
        return this.maxCapacity == this.currentLoad;
    }

    public long getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(long maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public long getCurrentLoad() {
        return currentLoad;
    }

    public void setCurrentLoad(long currentLoad) {
        this.currentLoad = currentLoad;
    }
}
