package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.AqiMeasurement;
import com.ecocode.scheduler.model.ComplexityScore;
import com.ecocode.scheduler.model.TaskRecord;
import com.ecocode.scheduler.model.TaskStatus;
import com.ecocode.scheduler.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Wires Layers 2-5 together:
 * 1. Fetch live AQI
 * 2. Generate Python
 * 3. Analyse complexity
 * 4. Dispatch to GPU node
 * 5. Execute Python with LIVE AQI values
 * 6. Save task
 */
@Service
public class TaskOrchestrationService {

    private final CodexClient codexClient;
    private final AqiClient aqiClient;
    private final TaskAnalyser taskAnalyser;
    private final TaskDispatcher taskDispatcher;
    private final PipelineRunner pipelineRunner;
    private final TaskRepository taskRepository;

    public TaskOrchestrationService(
            CodexClient codexClient,
            AqiClient aqiClient,
            TaskAnalyser taskAnalyser,
            TaskDispatcher taskDispatcher,
            PipelineRunner pipelineRunner,
            TaskRepository taskRepository) {
        this.codexClient = codexClient;
        this.aqiClient = aqiClient;
        this.taskAnalyser = taskAnalyser;
        this.taskDispatcher = taskDispatcher;
        this.pipelineRunner = pipelineRunner;
        this.taskRepository = taskRepository;
    }

    public TaskRecord submit(String description) {
        String taskId = "task-" + UUID.randomUUID().toString().substring(0, 8);
        TaskRecord task = new TaskRecord(taskId, description);
        taskRepository.save(task);

        try {
            task.setStatus(TaskStatus.RUNNING);

            // Fetch LIVE AQI data
            AqiMeasurement aqi = aqiClient.fetchLatest("Dhaka");

            // Generate Python code
            String code = codexClient.generatePipeline(description);
            task.setGeneratedCode(code);

            // Analyse complexity (checks both the generated code AND
            // the original description, so GPU-intent tasks are still
            // detected even if code generation failed/refused)
            ComplexityScore score = taskAnalyser.analyse(code, description);
            task.setComplexityUnits(score.getComplexityUnits());
            task.setGpuRequired(score.isGpuRequired());
            task.setBatchable(score.isBatchable());

            // Choose GPU node
            TaskDispatcher.DispatchDecision decision =
                    taskDispatcher.dispatch(score);
            task.setAssignedNode(decision.node().getId());
            task.setPreferredNode(
                    decision.preferredNode() != null ? decision.preferredNode().getId() : null);
            task.setFallbackOccurred(decision.fallbackOccurred());
            task.setEstimatedKwh(decision.energyEstimate().getKwhCost());
            task.setCo2Kg(decision.energyEstimate().getCo2Kg());
            task.setGreenScore(decision.energyEstimate().getGreenScore());

            // Execute using LIVE AQI values
            String result = pipelineRunner.run(code, aqi);
            task.setExecutionResult(result);

            task.setStatus(TaskStatus.COMPLETE);
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
        }

        taskRepository.save(task);
        return task;
    }
}