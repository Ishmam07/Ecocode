package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.ComplexityScore;
import com.ecocode.scheduler.model.EnergyEstimate;
import com.ecocode.scheduler.model.GpuNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Layer 3, part 2 - turns a ComplexityScore + a candidate node into an
 * energy estimate (kWh, CO2, and a "GreenScore" showing % saved versus
 * running the same task on the busiest / least efficient node).
 */
@Service
public class EnergyEstimator {

    private static final double BASE_KWH_PER_UNIT = 0.0032;

    @Value("${scheduler.co2-kg-per-kwh}")
    private double co2KgPerKwh;

    public EnergyEstimate estimate(ComplexityScore score, GpuNode node, double worstCaseMultiplier) {
        double kwh = BASE_KWH_PER_UNIT * node.getEnergyMultiplier() * score.getComplexityUnits();
        double baselineKwh = BASE_KWH_PER_UNIT * worstCaseMultiplier * score.getComplexityUnits();

        double co2 = kwh * co2KgPerKwh;
        double greenScore = baselineKwh == 0 ? 0 : (1 - (kwh / baselineKwh)) * 100;

        return new EnergyEstimate(kwh, co2, Math.max(0, greenScore));
    }
}
