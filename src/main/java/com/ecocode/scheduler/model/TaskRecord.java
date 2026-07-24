package com.ecocode.scheduler.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Full lifecycle state of one submitted task. This single class is the
 * JPA entity AND the JSON returned to the client - no separate
 * DTO/mapper layer, it is both, by design for this hackathon scope.
 */
@Entity
@Table(name = "tasks")
public class TaskRecord {

    @Id
    @Column(length = 40)
    private String taskId;

    @Column(length = 500)
    private String description;

    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.QUEUED;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String generatedCode;

    private String assignedNode;

    // The node the scheduler would have preferred for this task type
    // (GPU tasks -> Node A, batchable -> Node C, heavy CPU -> Node B),
    // regardless of whether it was available. Lets the frontend show
    // when a fallback happened.
    private String preferredNode;

    // True when assignedNode != preferredNode (the preferred node was
    // filtered out, e.g. because it was over the extreme-load threshold).
    private boolean fallbackOccurred;

    private double estimatedKwh;
    private double co2Kg;
    private double greenScore;

    // Debug visibility into the scheduler's decision - not in the original
    // blueprint's response shape, added so it's obvious in Postman *why*
    // a node was chosen.
    private double complexityUnits;
    private boolean gpuRequired;
    private boolean batchable;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String executionResult;

    private String errorMessage;

    // Required by JPA - do not remove.
    protected TaskRecord() {
    }

    public TaskRecord(String taskId, String description) {
        this.taskId = taskId;
        this.description = description;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getGeneratedCode() {
        return generatedCode;
    }

    public void setGeneratedCode(String generatedCode) {
        this.generatedCode = generatedCode;
    }

    public String getAssignedNode() {
        return assignedNode;
    }

    public void setAssignedNode(String assignedNode) {
        this.assignedNode = assignedNode;
    }

    public String getPreferredNode() {
        return preferredNode;
    }

    public void setPreferredNode(String preferredNode) {
        this.preferredNode = preferredNode;
    }

    public boolean isFallbackOccurred() {
        return fallbackOccurred;
    }

    public void setFallbackOccurred(boolean fallbackOccurred) {
        this.fallbackOccurred = fallbackOccurred;
    }

    public double getEstimatedKwh() {
        return estimatedKwh;
    }

    public void setEstimatedKwh(double estimatedKwh) {
        this.estimatedKwh = estimatedKwh;
    }

    public double getCo2Kg() {
        return co2Kg;
    }

    public void setCo2Kg(double co2Kg) {
        this.co2Kg = co2Kg;
    }

    public double getGreenScore() {
        return greenScore;
    }

    public void setGreenScore(double greenScore) {
        this.greenScore = greenScore;
    }

    public double getComplexityUnits() {
        return complexityUnits;
    }

    public void setComplexityUnits(double complexityUnits) {
        this.complexityUnits = complexityUnits;
    }

    public boolean isGpuRequired() {
        return gpuRequired;
    }

    public void setGpuRequired(boolean gpuRequired) {
        this.gpuRequired = gpuRequired;
    }

    public boolean isBatchable() {
        return batchable;
    }

    public void setBatchable(boolean batchable) {
        this.batchable = batchable;
    }

    public String getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(String executionResult) {
        this.executionResult = executionResult;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}