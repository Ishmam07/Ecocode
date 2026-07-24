package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.GpuNode;
import com.ecocode.scheduler.model.NodeType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Layer 4 - holds the 3 simulated GPU nodes in memory. No real GPU or
 * Kubernetes here; this is exactly the "3 in-memory objects" version
 * of the blueprint's cluster.
 */
@Service
public class GpuClusterService {

    @Value("${scheduler.node-a-load-pct}")
    private int nodeALoad;

    @Value("${scheduler.node-b-load-pct}")
    private int nodeBLoad;

    @Value("${scheduler.node-c-load-pct}")
    private int nodeCLoad;

    private List<GpuNode> nodes;

    @PostConstruct
    public void init() {
        nodes = List.of(
                new GpuNode("GPU_NODE_A", NodeType.HIGH_LOAD, nodeALoad, 1.4),
                new GpuNode("GPU_NODE_B", NodeType.MEDIUM, nodeBLoad, 1.0),
                new GpuNode("GPU_NODE_C", NodeType.LOW_IDLE, nodeCLoad, 0.9)
        );
    }

    public List<GpuNode> getNodes() {
        return nodes;
    }

    public GpuNode getById(String id) {
        return nodes.stream()
                .filter(n -> n.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown node id: " + id));
    }
}
