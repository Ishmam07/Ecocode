package com.ecocode.scheduler.model;

public class AqiMeasurement {

    private final String city;
    private final double pm25;
    private final double healthRiskIndex;
    private final double asthmaRiskPct;

    public AqiMeasurement(String city, double pm25, double healthRiskIndex, double asthmaRiskPct) {
        this.city = city;
        this.pm25 = pm25;
        this.healthRiskIndex = healthRiskIndex;
        this.asthmaRiskPct = asthmaRiskPct;
    }

    public String getCity() {
        return city;
    }

    public double getPm25() {
        return pm25;
    }

    public double getHealthRiskIndex() {
        return healthRiskIndex;
    }

    public double getAsthmaRiskPct() {
        return asthmaRiskPct;
    }
}
