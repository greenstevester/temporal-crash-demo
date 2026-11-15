package com.example.temporal;

public record DemoResult(
    String workflowId,
    String startedBy,
    long startedProcessId,
    String completedBy,
    long completedProcessId) {}
