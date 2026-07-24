package com.ecocode.scheduler.model;

import jakarta.validation.constraints.NotBlank;

/**
 * The request body for POST /api/v1/tasks/submit.
 * Example: { "description": "Predict asthma risk from PM2.5 data" }
 */
public class TaskRequest {

    @NotBlank(message = "description must not be empty")
    private String description;

    public TaskRequest() {
    }

    public TaskRequest(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
