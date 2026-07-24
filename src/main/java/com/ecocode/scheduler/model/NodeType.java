package com.ecocode.scheduler.model;

public enum NodeType {
    HIGH_LOAD,   // Node A - busy, avoid
    MEDIUM,      // Node B - balanced, preferred
    LOW_IDLE     // Node C - idle, used for batching small tasks
}
