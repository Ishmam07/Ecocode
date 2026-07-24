package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.ComplexityScore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaskAnalyser {

    @Value("${scheduler.batchable-threshold}")
    private double batchableThreshold;

    // Keywords that signal a GPU-class task even when the generated
    // code failed/refused and therefore never mentions a specific
    // library (e.g. "No model can be fine-tuned without TensorFlow...").
    private static final String[] GPU_INTENT_KEYWORDS = {
            "deep learning", "neural network", "cnn",
            "convolutional", "fine-tune", "fine tune",
            "gpu", "image classification", "object detection",
            "transformer", "training a model", "train a model"
    };

    public ComplexityScore analyse(String code) {
        return analyse(code, "");
    }

    public ComplexityScore analyse(String code, String description) {
        System.out.println("\n================ TASK ANALYSER ================");
        String lower = code.toLowerCase();
        String lowerDesc = description == null ? "" : description.toLowerCase();
        double complexity = 5.0;
        System.out.println("Base Complexity = " + complexity);

        // =====================
        // Machine Learning
        // =====================
        if (lower.contains("randomforest")
                || lower.contains("xgboost")
                || lower.contains("lightgbm")) {
            complexity += 40;
            System.out.println("+40 Tree ML");
        }

        if (lower.contains("tensorflow")
                || lower.contains("torch")
                || lower.contains("keras")) {
            complexity += 60;
            System.out.println("+60 Deep Learning");
        }

        // =====================
        // Data libraries
        // =====================
        if (lower.contains("numpy")) {
            complexity += 8;
            System.out.println("+8 NumPy");
        }
        if (lower.contains("pandas")) {
            complexity += 12;
            System.out.println("+12 Pandas");
        }

        // =====================
        // Loops
        // =====================
        int forLoops = count(lower, "for ");
        int whileLoops = count(lower, "while ");
        complexity += forLoops * 3;
        complexity += whileLoops * 4;
        System.out.println("For Loops = " + forLoops);
        System.out.println("While Loops = " + whileLoops);

        // =====================
        // Functions
        // =====================
        int functions = count(lower, "def ");
        complexity += functions * 2;
        System.out.println("Functions = " + functions);

        // =====================
        // JSON
        // =====================
        if (lower.contains("json")) {
            complexity += 2;
            System.out.println("+2 JSON");
        }

        // =====================
        // Dataset size
        // =====================
        if (lower.contains("1000000")
                || lower.contains("1_000_000")) {
            complexity += 30;
            System.out.println("+30 Large Dataset");
        }
        if (lower.contains("10000000")
                || lower.contains("10_000_000")) {
            complexity += 60;
            System.out.println("+60 Huge Dataset");
        }

        // =====================
        // GPU detection (from generated code)
        // =====================
        boolean gpuFromCode =
                lower.contains("tensorflow")
                        || lower.contains("torch")
                        || lower.contains("keras")
                        || lower.contains("cuda")
                        || lower.contains("cupy");

        // =====================
        // GPU detection (from the original task description)
        //
        // Falls back to intent keywords when the generated code
        // failed/refused and never actually imported a GPU library
        // (e.g. LLM returned an error message instead of code).
        // =====================
        boolean gpuFromDescription = false;
        for (String keyword : GPU_INTENT_KEYWORDS) {
            if (lowerDesc.contains(keyword)) {
                gpuFromDescription = true;
                break;
            }
        }

        boolean gpuRequired = gpuFromCode || gpuFromDescription;

        if (gpuRequired) {
            complexity += 20;
            System.out.println("+20 GPU (code=" + gpuFromCode + ", description=" + gpuFromDescription + ")");
        }

        boolean batchable = complexity <= batchableThreshold;

        System.out.println("--------------------------------");
        System.out.println("Final Complexity = " + complexity);
        System.out.println("GPU Required = " + gpuRequired);
        System.out.println("Batchable = " + batchable);
        System.out.println("================================");

        return new ComplexityScore(
                complexity,
                gpuRequired,
                batchable
        );
    }

    private int count(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }
}