package com.example.temporal;

public record StartReceipt(String workflowId, String workerName, long processId) {}
