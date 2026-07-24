package com.ecocode.scheduler.model;

/**
 * Output of the EnergyEstimator for one (task, node) pairing.
 */
public class EnergyEstimate {

    private final double kwhCost;
    private final double co2Kg;
    private final double greenScore; // % energy saved vs. the worst-case node

    public EnergyEstimate(double kwhCost, double co2Kg, double greenScore) {
        this.kwhCost = kwhCost;
        this.co2Kg = co2Kg;
        this.greenScore = greenScore;
    }

    public double getKwhCost() {
        return kwhCost;
    }

    public double getCo2Kg() {
        return co2Kg;
    }

    public double getGreenScore() {
        return greenScore;
    }
}
