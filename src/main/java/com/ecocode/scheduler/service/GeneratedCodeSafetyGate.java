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
 * Two layers are checked:
 *   1. CODE  - does the generated Python try to write outside the
 *      allowed working folder, or call a network/service library we
 *      did not explicitly allow (an "unknown service")?
 *   2. INTENT - does the *task description itself* ask for something
 *      unsafe (fetch external data, upload results, run system
 *      commands, execute dynamic code), even if the LLM ended up
 *      writing safe/mocked code instead of actually doing it? A task
 *      that asks for an unsafe capability is refused on intent alone
 *      - we don't want to depend on the LLM "happening" to fake the
 *      unsafe part safely every time.
 *
 * If either layer fails, the pipeline is REFUSED and never reaches
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

    // Phrases in the task DESCRIPTION that signal an unsafe capability
    // is being asked for, regardless of what the LLM actually wrote.
    // This closes the gap where the LLM "plays it safe" by mocking the
    // unsafe part (e.g. faking an upload instead of really uploading) -
    // we still don't want to reward/allow that request pattern.
    private static final List<String> UNSAFE_INTENT_KEYWORDS = List.of(
            "upload the result", "upload results", "upload to a server", "upload to server",
            "send to a server", "send to server", "remote server",
            "external api", "fetch live", "fetch data from",
            "call an api", "call the api", "http request", "network request",
            "download from", "post to", "send an email", "send email",
            "run a system command", "system command", "shell command",
            "execute a dynamically generated", "execute dynamic code",
            "delete files", "delete a file", "write to disk at",
            "access the filesystem", "read environment variables",
            "install a package", "pip install"
    );

    public SafetyGateResult inspect(String generatedCode) {
        return inspect(generatedCode, "");
    }

    public SafetyGateResult inspect(String generatedCode, String description) {
        if (generatedCode == null || generatedCode.isBlank()) {
            return SafetyGateResult.refused("Generated code was empty.");
        }

        String code = generatedCode;
        String lowerDesc = description == null ? "" : description.toLowerCase();

        // ===== Layer 1: inspect the generated CODE =====

        // 1a) Blocked module imports (network / OS / process control)
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

        // 1b) Dangerous dynamic-execution / filesystem calls
        for (String blockedCall : BLOCKED_CALLS) {
            if (code.contains(blockedCall)) {
                String reason = "Refused: calls '" + blockedCall.replace("(", "") +
                        "', which is not allowed in generated code.";
                log.warn("Safety gate REFUSED code — {}", reason);
                return SafetyGateResult.refused(reason);
            }
        }

        // 1c) Writes outside the allowed working folder
        Matcher pathMatcher = SUSPICIOUS_PATH_PATTERN.matcher(code);
        if (pathMatcher.find()) {
            String reason = "Refused: references a path outside the allowed working folder ("
                    + pathMatcher.group(1) + ").";
            log.warn("Safety gate REFUSED code — {}", reason);
            return SafetyGateResult.refused(reason);
        }

        // ===== Layer 2: inspect the task DESCRIPTION for unsafe intent =====
        //
        // Even if the LLM wrote safe/mocked code, a task that explicitly
        // asks for network access, file uploads, shell commands, or
        // dynamic code execution is refused on the request itself - we
        // don't rely on the LLM continuing to "play it safe" every time.
        for (String keyword : UNSAFE_INTENT_KEYWORDS) {
            if (lowerDesc.contains(keyword)) {
                String reason = "Refused: task description requests an unapproved capability ('"
                        + keyword + "').";
                log.warn("Safety gate REFUSED on intent — {}", reason);
                return SafetyGateResult.refused(reason);
            }
        }

        log.info("Safety gate PASSED generated code.");
        return SafetyGateResult.passed("Passed static safety check — no disallowed imports, calls, paths, or unsafe request intent.");
    }
}