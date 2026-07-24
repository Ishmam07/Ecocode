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

        GpuNode nodeA = nodes.stream()
                .filter(n -> n.getId().equals("GPU_NODE_A"))
                .findFirst()
                .orElse(null);

        GpuNode nodeB = nodes.stream()
                .filter(n -> n.getId().equals("GPU_NODE_B"))
                .findFirst()
                .orElse(null);

        GpuNode nodeC = nodes.stream()
                .filter(n -> n.getId().equals("GPU_NODE_C"))
                .findFirst()
                .orElse(null);

        GpuNode chosen = null;

        // ======================================
        // GPU Tasks
        // ======================================

        if (score.isGpuRequired()) {

            if (nodeA != null && nodeA.getLoadPercent() < LOAD_AVOID_THRESHOLD)
                chosen = nodeA;

            else if (nodeB != null)
                chosen = nodeB;

            else
                chosen = nodeC;
        }

        // ======================================
        // Small tasks
        // ======================================

        else if (score.isBatchable()) {

            if (nodeC != null)
                chosen = nodeC;

            else if (nodeB != null)
                chosen = nodeB;

            else
                chosen = nodeA;
        }

        // ======================================
        // Heavy CPU tasks
        // ======================================

        else {

            if (nodeB != null)
                chosen = nodeB;

            else if (nodeA != null)
                chosen = nodeA;

            else
                chosen = nodeC;
        }

        double worstCaseMultiplier =
                clusterService.getNodes()
                        .stream()
                        .mapToDouble(GpuNode::getEnergyMultiplier)
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

 