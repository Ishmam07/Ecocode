package com.ecocode.scheduler.exception;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(String taskId) {
        super("No task found with id: " + taskId);
    }
}
