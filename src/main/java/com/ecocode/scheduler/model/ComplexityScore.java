package com.ecocode.scheduler.model;

/**
 * Output of the TaskAnalyser: how "heavy" a generated pipeline is,
 * and whether it needs GPU-style resources.
 */
public class ComplexityScore {

    private final double complexityUnits;
    private final boolean gpuRequired;
    private final boolean batchable;

    public ComplexityScore(double complexityUnits, boolean gpuRequired, boolean batchable) {
        this.complexityUnits = complexityUnits;
        this.gpuRequired = gpuRequired;
        this.batchable = batchable;
    }

    public double getComplexityUnits() {
        return complexityUnits;
    }

    public boolean isGpuRequired() {
        return gpuRequired;
    }

    public boolean isBatchable() {
        return batchable;
    }
}
