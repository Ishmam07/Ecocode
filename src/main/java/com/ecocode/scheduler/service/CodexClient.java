package com.ecocode.scheduler.service;

import com.ecocode.scheduler.exception.LlmGenerationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodexClient {

    private static final Pattern CODE_FENCE =
            Pattern.compile("```(?:python)?\\s*([\\s\\S]*?)```", Pattern.MULTILINE);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    public String generatePipeline(String taskDescription) {

        String prompt = buildPrompt(taskDescription);

        try {

            String requestBody = mapper.writeValueAsString(
                    Map.of(
                            "model", model,
                            "messages", List.of(
                                    Map.of(
                                            "role", "user",
                                            "content", prompt
                                    )
                            )
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new LlmGenerationException(response.body());
            }

            JsonNode root = mapper.readTree(response.body());

            String text =
                    root.path("choices")
                            .get(0)
                            .path("message")
                            .path("content")
                            .asText();

            return extractCode(text);

        } catch (Exception e) {
            throw new LlmGenerationException("Groq call failed", e);
        }

    }

    /**
     * IMPORTANT:
     *
     * Groq DOES NOT receive live PM2.5 values anymore.
     *
     * It generates a reusable algorithm.
     *
     * Spring Boot injects:
     *
     * city
     * pm25
     * pm10
     * temperature
     *
     * before execution.
     */
    private String buildPrompt(String taskDescription) {
        return """
            You are a Python data pipeline generator.
            Output ONLY executable Python code - no markdown fences, no explanation text.

            CRITICAL - dependencies: This machine only has Python's standard
            library installed (no numpy, no pandas, no scikit-learn, no torch,
            no tensorflow). Do NOT import any third-party package unless the task
            description below explicitly names that exact library by name.
            Use only built-in modules: math, statistics, json, csv, random,
            datetime, itertools, collections. Implement any averaging, scaling,
            or regression logic manually using plain Python instead of a library.

            CRITICAL - data: Do NOT read from any external file (no pd.read_csv,
            no open(), no data.csv). There is no dataset file on disk. Instead,
            embed a small sample dataset directly in the code as a Python list
            of dicts (8-12 rows) with realistic values for these fields: city,
            pm25, pm10, temperature, timestamp (ISO format string). Use that
            embedded data for the task below.

            Task: %s

            Requirements:
            - Include a main() function with no arguments
            - The script must run standalone with no internet access, no file
              access, and no dependencies beyond the standard library, unless
              the task explicitly names a specific third-party library to use
            - Print the final result as a single line of valid JSON at the end
            - Keep it under 60 lines
            """.formatted(taskDescription);
    }
    private String extractCode(String raw) {

        Matcher matcher = CODE_FENCE.matcher(raw);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return raw.trim();

    }

}