package com.ecocode.scheduler.model;

/**
 * Result of running a generated pipeline through the
 * Generated Code Safety Gate.
 *
 * verdict   - PASSED, REFUSED (blocked before running) or STOPPED
 *             (killed mid-run for breaking a sandbox limit)
 * reason    - one simple sentence explaining the verdict, shown on
 *             the dashboard's gate record
 */
public record SafetyGateResult(
        GateVerdict verdict,
        String reason
) {
    public static SafetyGateResult passed(String reason) {
        return new SafetyGateResult(GateVerdict.PASSED, reason);
    }

    public static SafetyGateResult refused(String reason) {
        return new SafetyGateResult(GateVerdict.REFUSED, reason);
    }

    public static SafetyGateResult stopped(String reason) {
        return new SafetyGateResult(GateVerdict.STOPPED, reason);
    }

    public boolean isPassed() {
        return verdict == GateVerdict.PASSED;
    }
}