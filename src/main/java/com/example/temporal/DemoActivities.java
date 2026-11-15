package com.example.temporal;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface DemoActivities {
  StartReceipt recordStart(String workflowId);

  DemoResult recordCompletion(StartReceipt receipt);
}
