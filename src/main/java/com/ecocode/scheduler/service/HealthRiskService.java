package com.ecocode.scheduler.service;

import org.springframework.stereotype.Service;

/**
 * Simple WHO-guideline-based health risk formula (same as the blueprint):
 * healthRiskIndex = min(1.0, pm25 / 75.0)
 * asthmaRiskPct = healthRiskIndex * 34
 */
@Service
public class HealthRiskService {

    public double computeHealthRiskIndex(double pm25) {
        return Math.min(1.0, pm25 / 75.0);
    }

    public double computeAsthmaRiskPct(double healthRiskIndex) {
        return healthRiskIndex * 34;
    }
}
