package com.example.temporal;

import java.time.Duration;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class DemoWorkflowImpl implements DemoWorkflow {
  private final DemoActivities activities = Workflow.newActivityStub(
      DemoActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(5))
          .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
          .build());

  @Override
  public DemoResult run() {
    StartReceipt receipt = activities.recordStart(Workflow.getInfo().getWorkflowId());
    Workflow.sleep(Duration.ofSeconds(10));
    return activities.recordCompletion(receipt);
  }
}
