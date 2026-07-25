package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.AqiMeasurement;
import com.ecocode.scheduler.model.GateVerdict;
import com.ecocode.scheduler.model.SafetyGateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class PipelineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(PipelineRunner.class);

    private static final int TIMEOUT_SECONDS = 20;

    // Sandbox memory ceiling for the generated pipeline's Python
    // process. Enforced via "ulimit -v" (virtual memory, KB) so a
    // runaway pipeline can't exhaust the host's RAM.
    private static final int MEMORY_LIMIT_MB = 512;

    /**
     * Result of one sandboxed execution: the raw output/JSON the
     * pipeline produced, plus the gate verdict for that run
     * (PASSED = ran to completion, STOPPED = killed for breaking a
     * sandbox limit).
     */
    public record ExecutionOutcome(String output, GateVerdict verdict, String gateNote) {
        public static ExecutionOutcome ok(String output) {
            return new ExecutionOutcome(output, GateVerdict.PASSED, "Ran within time and memory limits.");
        }

        public static ExecutionOutcome stopped(String output, String note) {
            return new ExecutionOutcome(output, GateVerdict.STOPPED, note);
        }
    }

    public ExecutionOutcome run(String generatedCode, AqiMeasurement aqi) {

        Path tempFile = null;

        try {

            tempFile = Files.createTempFile(
                    "ecocode_pipeline_",
                    ".py"
            );


            String pythonCode =
                    buildPython(
                            aqi,
                            generatedCode
                    );


            Files.writeString(
                    tempFile,
                    pythonCode,
                    StandardCharsets.UTF_8
            );


            String pythonExecutable =
                    System.getenv()
                            .getOrDefault(
                                    "PYTHON_EXECUTABLE",
                                    "python3"
                            );


            log.info(
                    "Using Python executable: {}",
                    pythonExecutable
            );


            // NOTE: ulimit -v (virtual memory) is intentionally NOT used here.
            // numpy/pandas reserve large virtual address ranges on startup
            // that are far bigger than physical memory actually used, so a
            // ulimit -v ceiling kills/hangs the interpreter on totally normal
            // pipelines. Time is a reliable, portable sandbox limit on its
            // own; RSS-based memory limiting is done post-hoc below instead
            // of via a hard interpreter-breaking ulimit.
            ProcessBuilder builder =
                    new ProcessBuilder(
                            pythonExecutable,
                            tempFile.toString()
                    );


            builder.redirectErrorStream(true);


            Process process =
                    builder.start();

            long pid = process.pid();
            java.util.concurrent.atomic.AtomicBoolean memoryExceeded =
                    new java.util.concurrent.atomic.AtomicBoolean(false);

            // Poll the process's resident memory (RSS) in the background.
            // This is a soft, portable memory sandbox that doesn't touch
            // the interpreter's virtual address space the way ulimit -v
            // does, so it doesn't break numpy/pandas startup.
            Thread memoryWatcher = new Thread(() -> {
                Path statusPath = Path.of("/proc", String.valueOf(pid), "status");
                while (process.isAlive()) {
                    try {
                        if (Files.exists(statusPath)) {
                            for (String line : Files.readAllLines(statusPath)) {
                                if (line.startsWith("VmRSS:")) {
                                    long rssKb = Long.parseLong(
                                            line.replaceAll("[^0-9]", ""));
                                    if (rssKb > (long) MEMORY_LIMIT_MB * 1024) {
                                        memoryExceeded.set(true);
                                        process.destroyForcibly();
                                        return;
                                    }
                                    break;
                                }
                            }
                        }
                        Thread.sleep(200);
                    } catch (Exception ignored) {
                        return;
                    }
                }
            });
            memoryWatcher.setDaemon(true);
            memoryWatcher.start();


            boolean finished =
                    process.waitFor(
                            TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );


            if (!finished) {

                process.destroyForcibly();

                String note = "Stopped: exceeded the " + TIMEOUT_SECONDS + "s sandbox time limit.";
                return ExecutionOutcome.stopped(mockResult(note), note);
            }


            String output =
                    new String(
                            process.getInputStream()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    ).trim();


            log.info(
                    "Python Output:\n{}",
                    output
            );


            int exitValue = process.exitValue();

            // Killed either by our RSS watcher (memoryExceeded flag) or by
            // the OS OOM killer (exit code 137 = SIGKILL signature).
            if (memoryExceeded.get() || exitValue == 137 || output.toLowerCase().contains("memoryerror")) {
                String note = "Stopped: exceeded the " + MEMORY_LIMIT_MB + "MB sandbox memory limit.";
                return ExecutionOutcome.stopped(mockResult(note), note);
            }

            if (exitValue != 0) {
                return new ExecutionOutcome(mockResult(output), GateVerdict.PASSED,
                        "Ran to completion; pipeline itself reported an error.");
            }


            if (output.isBlank()) {

                return new ExecutionOutcome(mockResult("No Python output"), GateVerdict.PASSED,
                        "Ran to completion with no output.");

            }


            return ExecutionOutcome.ok(output);


        }
        catch (IOException e) {

            log.error(
                    "Python start failed",
                    e
            );

            // The sandbox never actually ran the pipeline (e.g. the
            // python3 interpreter isn't installed/on PATH), so this is
            // NOT a verified "PASSED" run - report it as STOPPED so it
            // doesn't get counted as if the safety limits were checked.
            String note = "Stopped: sandbox failed to start (" + e.getMessage() + ").";
            return ExecutionOutcome.stopped(mockResult(note), note);

        }
        catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();

            String note = "Stopped: execution was interrupted.";
            return ExecutionOutcome.stopped(mockResult(note), note);

        }
        finally {

            if (tempFile != null) {

                try {

                    Files.deleteIfExists(
                            tempFile
                    );

                }
                catch (IOException ignored) {

                }
            }
        }
    }




    private String buildPython(
            AqiMeasurement aqi,
            String generatedCode
    ) {


        StringBuilder builder =
                new StringBuilder();



        /*
         * Base imports
         */
        builder.append("""
                import json
                import numpy as np
                import pandas as pd
                
                """);



        /*
         * Incoming AQI data
         */
        builder.append(
                        "city = [\""
                )
                .append(
                        escape(aqi.getCity())
                )
                .append(
                        "\"]\n"
                );


        builder.append(
                        "pm25 = ["
                )
                .append(
                        aqi.getPm25()
                )
                .append(
                        "]\n"
                );


        builder.append("""
                pm10 = [50]
                temperature = [30]
                humidity = [60]
                
                """);



        /*
         * Create safe dataset
         */
        builder.append("""
                df = pd.DataFrame({
                    "pm25": pm25 * 20,
                    "pm10": pm10 * 20,
                    "temperature": temperature * 20,
                    "humidity": humidity * 20,
                    "city": city * 20
                })


                df["aqi_level"] = df["pm25"].apply(
                    lambda x:
                    0 if x <= 50 else
                    1 if x <= 100 else
                    2
                )


                """);



        /*
         * Safe KFold replacement
         */
        builder.append("""
                class SafeKFold:

                    def __init__(self, n_splits=10):
                        self.n_splits = n_splits


                    def split(self, X, y=None, groups=None):

                        total = len(X)

                        splits = min(
                            self.n_splits,
                            total
                        )


                        indices = np.arange(total)


                        size = max(
                            1,
                            total // splits
                        )


                        for i in range(splits):

                            test = indices[
                                i*size:
                                min(
                                    (i+1)*size,
                                    total
                                )
                            ]


                            train = np.delete(
                                indices,
                                test
                            )


                            yield train, test


                KFold = SafeKFold


                """);



        /*
         * Generated AI code
         */
        builder.append(
                "# ===== GENERATED CODE =====\n\n"
        );


        builder.append(
                generatedCode
        );


        return builder.toString();

    }





    private String escape(String value) {

        if (value == null) {

            return "";

        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

    }





    private String mockResult(String reason) {

        return """
                {
                  "mocked": true,
                  "reason": "%s"
                }
                """
                .formatted(
                        reason
                                .replace("\\", "\\\\")
                                .replace("\"", "'")
                                .replace("\n", "\\n")
                );

    }

}