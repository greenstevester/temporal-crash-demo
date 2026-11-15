package com.example.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicReference;

import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.Test;

class DemoWorkflowTest {
  @Test
  void shouldPassTheRecordedStartReceiptToTheCompletionActivity() {
    StartReceipt receipt = new StartReceipt("unit-workflow", "first-worker", 11);
    DemoResult expected = new DemoResult("unit-workflow", "first-worker", 11, "second-worker", 22);
    // Record what the fakes receive and assert afterwards. An assertion thrown inside an activity
    // becomes a retried activity failure and hides the real message.
    var receivedWorkflowId = new AtomicReference<String>();
    var receivedReceipt = new AtomicReference<StartReceipt>();
    DemoActivities activities = new DemoActivities() {
      public StartReceipt recordStart(String workflowId) {
        receivedWorkflowId.set(workflowId);
        return receipt;
      }

      public DemoResult recordCompletion(StartReceipt actualReceipt) {
        receivedReceipt.set(actualReceipt);
        return expected;
      }
    };

    try (var environment = TestWorkflowEnvironment.newInstance()) {
      DemoWorkflow workflow = register(environment, activities);

      DemoResult result = workflow.run();

      assertEquals(expected, result);
      assertEquals(receipt.workflowId(), receivedWorkflowId.get());
      assertEquals(receipt, receivedReceipt.get());
    }
  }

  @Test
  void shouldFailWithoutCompletingWhenTheFirstActivityHasAPermanentFailure() {
    int[] completionCalls = {0};
    DemoActivities activities = new DemoActivities() {
      public StartReceipt recordStart(String workflowId) {
        throw ApplicationFailure.newNonRetryableFailure("Recording failed", "RecordingFailure");
      }

      public DemoResult recordCompletion(StartReceipt receipt) {
        completionCalls[0]++;
        return null;
      }
    };

    try (var environment = TestWorkflowEnvironment.newInstance()) {
      DemoWorkflow workflow = register(environment, activities);

      assertThrows(WorkflowFailedException.class, workflow::run);

      assertEquals(0, completionCalls[0]);
    }
  }

  private static DemoWorkflow register(TestWorkflowEnvironment environment, DemoActivities activities) {
    var worker = environment.newWorker("crash-demo");
    worker.registerWorkflowImplementationTypes(DemoWorkflowImpl.class);
    worker.registerActivitiesImplementations(activities);
    environment.start();
    return environment.getWorkflowClient().newWorkflowStub(DemoWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue("crash-demo").setWorkflowId("unit-workflow").build());
  }
}
