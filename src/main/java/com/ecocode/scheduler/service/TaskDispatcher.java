package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.ComplexityScore;
import com.ecocode.scheduler.model.EnergyEstimate;
import com.ecocode.scheduler.model.GpuNode;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class TaskDispatcher {

    private static final int LOAD_AVOID_THRESHOLD = 70;
    private static final int EXTREME_LOAD_THRESHOLD = 90;

    private final GpuClusterService clusterService;
    private final EnergyEstimator energyEstimator;


    public TaskDispatcher(
            GpuClusterService clusterService,
            EnergyEstimator energyEstimator
    ) {
        this.clusterService = clusterService;
        this.energyEstimator = energyEstimator;
    }


    public record DispatchDecision(
            GpuNode node,
            EnergyEstimate energyEstimate
    ) {
    }


    public DispatchDecision dispatch(ComplexityScore score) {

        List<GpuNode> nodes =
                clusterService.getNodes()
                        .stream()
                        .filter(node ->
                                node.getLoadPercent() < EXTREME_LOAD_THRESHOLD)
                        .toList();

        if (nodes.isEmpty()) {
            throw new RuntimeException("No available node.");
        }

        GpuNode chosen;

        // ======================================
        // GPU Tasks
        // ======================================

        if (score.isGpuRequired()) {

            chosen = nodes.stream()
                    .min(Comparator.comparingInt(
                            GpuNode::getLoadPercent
                    ))
                    .orElseThrow();
        }

        // ======================================
        // Small / Batchable Tasks
        // ======================================

        else if (score.isBatchable()) {

            chosen = nodes.stream()
                    .min(Comparator.comparingInt(
                            GpuNode::getLoadPercent
                    ))
                    .orElseThrow();
        }

        // ======================================
        // Heavy CPU Tasks
        // ======================================

        else {

            chosen = nodes.stream()
                    .min(Comparator.comparingInt(
                            GpuNode::getLoadPercent
                    ))
                    .orElseThrow();
        }

        double worstCaseMultiplier =
                clusterService.getNodes()
                        .stream()
                        .mapToDouble(
                                GpuNode::getEnergyMultiplier
                        )
                        .max()
                        .orElse(1.0);

        return new DispatchDecision(
                chosen,
                energyEstimator.estimate(
                        score,
                        chosen,
                        worstCaseMultiplier
                )
        );
    }
}

