package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.AqiMeasurement;
import com.ecocode.scheduler.model.ComplexityScore;
import com.ecocode.scheduler.model.GateVerdict;
import com.ecocode.scheduler.model.SafetyGateResult;
import com.ecocode.scheduler.model.TaskRecord;
import com.ecocode.scheduler.model.TaskStatus;
import com.ecocode.scheduler.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Wires the layers together:
 * 1. Fetch live AQI
 * 2. Generate Python
 * 3. Generated Code Safety Gate  <-- inspects the code BEFORE scheduling
 * 4. Analyse complexity
 * 5. Dispatch to GPU node
 * 6. Execute Python with LIVE AQI values (sandboxed: time + memory limit)
 * 7. Save task, including the gate's verdict/reason
 */
@Service
public class TaskOrchestrationService {

    private final CodexClient codexClient;
    private final AqiClient aqiClient;
    private final GeneratedCodeSafetyGate safetyGate;
    private final TaskAnalyser taskAnalyser;
    private final TaskDispatcher taskDispatcher;
    private final PipelineRunner pipelineRunner;
    private final TaskRepository taskRepository;

    public TaskOrchestrationService(
            CodexClient codexClient,
            AqiClient aqiClient,
            GeneratedCodeSafetyGate safetyGate,
            TaskAnalyser taskAnalyser,
            TaskDispatcher taskDispatcher,
            PipelineRunner pipelineRunner,
            TaskRepository taskRepository) {
        this.codexClient = codexClient;
        this.aqiClient = aqiClient;
        this.safetyGate = safetyGate;
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

            // ============================================
            // Generated Code Safety Gate
            //
            // Sits between the generator and the scheduler.
            // Refused code never reaches the dispatcher or the
            // sandbox - no node is wasted running it.
            // ============================================
            SafetyGateResult gateResult = safetyGate.inspect(code, description);
            task.setGateVerdict(gateResult.verdict());
            task.setGateReason(gateResult.reason());

            if (!gateResult.isPassed()) {
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMessage("Blocked by Generated Code Safety Gate: " + gateResult.reason());
                taskRepository.save(task);
                return task;
            }

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

            // Execute using LIVE AQI values, inside the sandbox
            // (time limit + memory limit enforced in PipelineRunner)
            PipelineRunner.ExecutionOutcome outcome = pipelineRunner.run(code, aqi);
            task.setExecutionResult(outcome.output());

            // The sandbox can still STOP a pipeline that passed the
            // static gate check (e.g. it runs too long or uses too
            // much memory) - record that as the final gate verdict.
            if (outcome.verdict() == GateVerdict.STOPPED) {
                task.setGateVerdict(GateVerdict.STOPPED);
                task.setGateReason(outcome.gateNote());
            }

            task.setStatus(TaskStatus.COMPLETE);
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
        }

        taskRepository.save(task);
        return task;
    }
}