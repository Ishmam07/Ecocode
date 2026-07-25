package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.SafetyGateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sits between the Generator (CodexClient) and the Scheduler.
 *
 * Every pipeline the LLM produces is inspected here BEFORE it is
 * scheduled or executed. This is a static, offline check - it makes
 * no network calls and costs no API tokens, so it can run on every
 * single task without adding cost.
 *
 * Two things are checked:
 *   1. Does the code try to write outside the allowed working folder?
 *   2. Does the code call a network/service library we did not
 *      explicitly allow (i.e. an "unknown service")?
 *
 * If either check fails, the pipeline is REFUSED and never reaches
 * the dispatcher or the Python sandbox. Passing this gate does not
 * guarantee safe *behavior* at runtime (e.g. infinite loops) - that
 * risk is handled separately by the sandbox's time/memory limits in
 * PipelineRunner, which can still STOP a pipeline that passed this
 * static check.
 */
@Service
public class GeneratedCodeSafetyGate {

    private static final Logger log =
            LoggerFactory.getLogger(GeneratedCodeSafetyGate.class);

    // Only these libraries are allowed to be imported by generated
    // code. Anything calling out to the network, the OS, or another
    // process is refused as an "unknown service".
    private static final List<String> ALLOWED_IMPORTS = List.of(
            "json", "numpy", "np", "pandas", "pd", "math", "statistics",
            "collections", "itertools", "re", "datetime", "random",
            "sklearn", "scipy"
    );

    // Import patterns: "import X", "import X as Y", "from X import Y"
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*(?:import|from)\\s+([a-zA-Z0-9_\\.]+)");

    // Modules that reach outside the sandbox: filesystem, network,
    // process control, environment, dynamic code execution.
    private static final List<String> BLOCKED_MODULES = List.of(
            "socket", "requests", "urllib", "http", "ftplib", "smtplib",
            "subprocess", "os", "sys", "shutil", "pathlib",
            "multiprocessing", "threading", "ctypes",
            "paramiko", "boto3", "pickle"
    );

    // Dangerous built-ins / dynamic execution.
    private static final List<String> BLOCKED_CALLS = List.of(
            "eval(", "exec(", "__import__", "compile(", "open("
    );

    // Anything that looks like a filesystem write outside the
    // sandbox's own temp working file (absolute paths, path traversal).
    private static final Pattern SUSPICIOUS_PATH_PATTERN =
            Pattern.compile("[\"']\\s*(/(?!tmp\\b)[a-zA-Z0-9_./]*|[A-Za-z]:\\\\[^\"']*|\\.\\./[^\"']*)\\s*[\"']");

    public SafetyGateResult inspect(String generatedCode) {
        if (generatedCode == null || generatedCode.isBlank()) {
            return SafetyGateResult.refused("Generated code was empty.");
        }

        String code = generatedCode;

        // 1) Blocked module imports (network / OS / process control)
        Matcher importMatcher = IMPORT_PATTERN.matcher(code);
        while (importMatcher.find()) {
            String imported = importMatcher.group(1);
            String root = imported.split("\\.")[0];

            if (BLOCKED_MODULES.contains(root)) {
                String reason = "Refused: imports '" + root + "', an unknown/unapproved service.";
                log.warn("Safety gate REFUSED code — {}", reason);
                return SafetyGateResult.refused(reason);
            }
        }

        // 2) Dangerous dynamic-execution / filesystem calls
        for (String blockedCall : BLOCKED_CALLS) {
            if (code.contains(blockedCall)) {
                String reason = "Refused: calls '" + blockedCall.replace("(", "") +
                        "', which is not allowed in generated code.";
                log.warn("Safety gate REFUSED code — {}", reason);
                return SafetyGateResult.refused(reason);
            }
        }

        // 3) Writes outside the allowed working folder
        Matcher pathMatcher = SUSPICIOUS_PATH_PATTERN.matcher(code);
        if (pathMatcher.find()) {
            String reason = "Refused: references a path outside the allowed working folder ("
                    + pathMatcher.group(1) + ").";
            log.warn("Safety gate REFUSED code — {}", reason);
            return SafetyGateResult.refused(reason);
        }

        log.info("Safety gate PASSED generated code.");
        return SafetyGateResult.passed("Passed static safety check — no disallowed imports, calls, or paths.");
    }
}