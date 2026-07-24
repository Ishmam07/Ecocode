package com.ecocode.scheduler.model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents one simulated GPU node. No real GPU here - just an in-memory
 * object with a load percentage and an energy multiplier, matching the
 * three nodes from the blueprint (A = busy, B = balanced, C = idle).
 */
public class GpuNode {

    private final String id;
    private final NodeType type;
    private final AtomicInteger loadPercent;
    private final double energyMultiplier;
    private final AtomicInteger queueDepth = new AtomicInteger(0);

    public GpuNode(String id, NodeType type, int initialLoadPercent, double energyMultiplier) {
        this.id = id;
        this.type = type;
        this.loadPercent = new AtomicInteger(initialLoadPercent);
        this.energyMultiplier = energyMultiplier;
    }

    public String getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }

    public int getLoadPercent() {
        return loadPercent.get();
    }

    public void setLoadPercent(int value) {
        loadPercent.set(value);
    }

    public double getEnergyMultiplier() {
        return energyMultiplier;
    }

    public int getQueueDepth() {
        return queueDepth.get();
    }

    public void incrementQueue() {
        queueDepth.incrementAndGet();
    }

    public void decrementQueue() {
        queueDepth.updateAndGet(v -> Math.max(0, v - 1));
    }
}
