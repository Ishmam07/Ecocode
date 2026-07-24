package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.ComplexityScore;
import com.ecocode.scheduler.model.EnergyEstimate;
import com.ecocode.scheduler.model.GpuNode;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class TaskDispatcher {

    // Node is considered extremely busy above this load
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
            GpuNode preferredNode,
            boolean fallbackOccurred,
            EnergyEstimate energyEstimate
    ) {
    }

    public DispatchDecision dispatch(ComplexityScore score) {

        // Full cluster (unfiltered) — used to know what the "ideal" node
        // would have been, even if it's currently overloaded.
        List<GpuNode> allNodes = clusterService.getNodes();

        GpuNode idealNodeA = findById(allNodes, "GPU_NODE_A");
        GpuNode idealNodeB = findById(allNodes, "GPU_NODE_B");
        GpuNode idealNodeC = findById(allNodes, "GPU_NODE_C");

        // Only exclude nodes that are extremely busy
        List<GpuNode> nodes =
                allNodes.stream()
                        .filter(node ->
                                node.getLoadPercent() < EXTREME_LOAD_THRESHOLD)
                        .toList();

        if (nodes.isEmpty()) {
            throw new RuntimeException("No available node.");
        }


        // ======================================
        // Find Nodes (available only)
        // ======================================

        GpuNode nodeA = nodes.stream()
                .filter(n ->
                        n.getId().equals("GPU_NODE_A"))
                .findFirst()
                .orElse(null);

        GpuNode nodeB = nodes.stream()
                .filter(n ->
                        n.getId().equals("GPU_NODE_B"))
                .findFirst()
                .orElse(null);

        GpuNode nodeC = nodes.stream()
                .filter(n ->
                        n.getId().equals("GPU_NODE_C"))
                .findFirst()
                .orElse(null);


        GpuNode chosen = null;
        GpuNode preferred;


        // ======================================
        // GPU / AI Tasks
        // ======================================

        if (score.isGpuRequired()) {

            /*
             * GPU tasks prefer Node A.
             *
             * Node A can still be selected even if
             * its load is above 70%.
             *
             * Only 90%+ load is considered extreme
             * and filtered out above.
             */

            preferred = idealNodeA;

            if (nodeA != null) {
                chosen = nodeA;
            }

            else if (nodeB != null) {
                chosen = nodeB;
            }

            else {
                chosen = nodeC;
            }
        }


        // ======================================
        // Small / Batchable Tasks
        // ======================================

        else if (score.isBatchable()) {

            /*
             * Batchable tasks prefer Node C.
             */

            preferred = idealNodeC;

            if (nodeC != null) {
                chosen = nodeC;
            }

            else if (nodeB != null) {
                chosen = nodeB;
            }

            else {
                chosen = nodeA;
            }
        }


        // ======================================
        // Heavy CPU Tasks
        // ======================================

        else {

            /*
             * Heavy CPU tasks prefer Node B.
             */

            preferred = idealNodeB;

            if (nodeB != null) {
                chosen = nodeB;
            }

            else if (nodeA != null) {
                chosen = nodeA;
            }

            else {
                chosen = nodeC;
            }
        }


        // ======================================
        // Safety Check
        // ======================================

        if (chosen == null) {
            throw new RuntimeException(
                    "Unable to select a node."
            );
        }

        // Fallback happened if the node we actually picked isn't the
        // preferred one (either because it was overloaded/excluded,
        // or simply missing from the cluster).
        boolean fallbackOccurred =
                preferred == null
                        || !preferred.getId().equals(chosen.getId());


        // ======================================
        // Energy Estimation
        // ======================================

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
                preferred,
                fallbackOccurred,
                energyEstimator.estimate(
                        score,
                        chosen,
                        worstCaseMultiplier
                )
        );
    }

    private GpuNode findById(List<GpuNode> nodes, String id) {
        return nodes.stream()
                .filter(n -> n.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}