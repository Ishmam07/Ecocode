package com.ecocode.scheduler.controller;

import com.ecocode.scheduler.exception.TaskNotFoundException;
import com.ecocode.scheduler.model.TaskRecord;
import com.ecocode.scheduler.model.TaskRequest;
import com.ecocode.scheduler.service.TaskOrchestrationService;
import com.ecocode.scheduler.repository.TaskRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskOrchestrationService orchestrationService;
    private final TaskRepository taskRepository;

    public TaskController(TaskOrchestrationService orchestrationService, TaskRepository taskRepository) {
        this.orchestrationService = orchestrationService;
        this.taskRepository = taskRepository;
    }

    // POST http://localhost:8080/api/v1/tasks/submit
    // Body: { "description": "Predict asthma risk from PM2.5 data" }
    @PostMapping("/submit")
    public ResponseEntity<TaskRecord> submit(@Valid @RequestBody TaskRequest request) {
        TaskRecord task = orchestrationService.submit(request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    // GET http://localhost:8080/api/v1/tasks
    @GetMapping
    public List<TaskRecord> listAll() {
        return taskRepository.findAll();
    }

    // GET http://localhost:8080/api/v1/tasks/{id}
    @GetMapping("/{id}")
    public TaskRecord getById(@PathVariable String id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    // GET http://localhost:8080/api/v1/tasks/{id}/code
    @GetMapping("/{id}/code")
    public ResponseEntity<String> getCode(@PathVariable String id) {
        TaskRecord task = taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        return ResponseEntity.ok(task.getGeneratedCode());
    }

    // GET http://localhost:8080/api/v1/tasks/{id}/result
    @GetMapping("/{id}/result")
    public ResponseEntity<String> getResult(@PathVariable String id) {
        TaskRecord task = taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
        return ResponseEntity.ok(task.getExecutionResult());
    }
}
