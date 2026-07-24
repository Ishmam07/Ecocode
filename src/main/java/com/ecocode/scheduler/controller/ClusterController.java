package com.ecocode.scheduler.controller;

import com.ecocode.scheduler.model.GpuNode;
import com.ecocode.scheduler.service.GpuClusterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cluster")
public class ClusterController {

    private final GpuClusterService clusterService;

    public ClusterController(GpuClusterService clusterService) {
        this.clusterService = clusterService;
    }

    // GET http://localhost:8080/api/v1/cluster/nodes
    @GetMapping("/nodes")
    public List<GpuNode> getNodes() {
        return clusterService.getNodes();
    }

    // GET http://localhost:8080/api/v1/cluster/nodes/{id}
    @GetMapping("/nodes/{id}")
    public GpuNode getNode(@PathVariable String id) {
        return clusterService.getById(id);
    }
}
