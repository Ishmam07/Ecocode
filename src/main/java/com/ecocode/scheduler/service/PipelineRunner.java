package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.AqiMeasurement;
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


    public String run(String generatedCode, AqiMeasurement aqi) {

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


            ProcessBuilder builder =
                    new ProcessBuilder(
                            pythonExecutable,
                            tempFile.toString()
                    );


            builder.redirectErrorStream(true);


            Process process =
                    builder.start();


            boolean finished =
                    process.waitFor(
                            TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );


            if (!finished) {

                process.destroyForcibly();

                return mockResult(
                        "Python execution timeout"
                );
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


            if (process.exitValue() != 0) {

                return mockResult(output);

            }


            if (output.isBlank()) {

                return mockResult(
                        "No Python output"
                );

            }


            return output;


        }
        catch (IOException e) {

            log.error(
                    "Python start failed",
                    e
            );

            return mockResult(
                    e.getMessage()
            );

        }
        catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();

            return mockResult(
                    "Execution interrupted"
            );

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