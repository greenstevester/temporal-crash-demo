package com.example.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.history.v1.History;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class CrashRestartIT {
  @Test
  @Timeout(180)
  void shouldFinishTheSameExecutionAfterItsSpringWorkerIsKilled() throws Exception {
    Path evidence = Path.of("target", "crash-restart-evidence", UUID.randomUUID().toString())
        .toAbsolutePath();
    Files.createDirectories(evidence);
    Path audit = evidence.resolve("activity-calls");
    Process firstWorker = null;
    Process secondWorker = null;
    Process server = null;
    WorkflowServiceStubs service = null;
    String address = null;
    Throwable failure = null;
    // Kill owned processes if the test JVM exits without reaching the finally block, e.g. Ctrl-C.
    List<Process> owned = new CopyOnWriteArrayList<>();
    Thread killOnExit = new Thread(() -> owned.forEach(Process::destroyForcibly));
    Runtime.getRuntime().addShutdownHook(killOnExit);

    try {
      // Another program can take the free port before Temporal binds it, so try one fresh port.
      for (int attempt = 1; service == null; attempt++) {
        int port = freeLoopbackPort();
        String candidate = "127.0.0.1:" + port;
        server = startServer(port, evidence);
        owned.add(server);
        try {
          service = WorkflowServiceStubs.newConnectedServiceStubs(
              WorkflowServiceStubsOptions.newBuilder().setTarget(candidate).build(),
              Duration.ofSeconds(45));
          address = candidate;
        } catch (RuntimeException error) {
          if (attempt == 2) {
            throw error;
          }
          System.out.println("[demo] Temporal did not start on " + candidate + ", trying a new port");
          stopProcesses(server);
          server = null;
        }
      }
      WorkflowClient client = WorkflowClient.newInstance(service);
      String workflowId = "crash-demo-" + UUID.randomUUID();
      DemoWorkflow workflow = client.newWorkflowStub(DemoWorkflow.class,
          WorkflowOptions.newBuilder().setWorkflowId(workflowId).setTaskQueue("crash-demo").build());

      firstWorker = startWorker(address, "before-crash", audit, evidence.resolve("worker-before.log"));
      owned.add(firstWorker);
      awaitReady(firstWorker, evidence.resolve("worker-before.log"));
      WorkflowExecution execution = WorkflowClient.start(workflow::run);
      System.out.println("[demo] Started workflow " + execution.getWorkflowId());
      System.out.println("[demo] Original run " + execution.getRunId());

      WorkflowServiceStubs connectedService = service;
      await("saved activity result and timer start", Duration.ofSeconds(30),
          () -> hasEvent(history(connectedService, execution), EventType.EVENT_TYPE_TIMER_STARTED));
      History beforeCrash = history(service, execution);
      Files.writeString(evidence.resolve("history-before.txt"), beforeCrash.toString());
      assertEquals(1, count(beforeCrash, EventType.EVENT_TYPE_ACTIVITY_TASK_COMPLETED));
      assertEquals(1, Files.readAllLines(audit.resolve("started.txt")).size());

      long firstPid = firstWorker.pid();
      System.out.println("[demo] Force-killing Spring worker " + firstPid);
      firstWorker.destroyForcibly();
      assertTrue(firstWorker.waitFor(10, TimeUnit.SECONDS), "First worker must exit");
      assertFalse(firstWorker.isAlive());
      assertNotEquals(0, firstWorker.exitValue(), "The worker must stop abnormally");

      await("timer to fire while the worker is down", Duration.ofSeconds(30),
          () -> hasEvent(history(connectedService, execution), EventType.EVENT_TYPE_TIMER_FIRED));
      assertFalse(Files.exists(audit.resolve("completed.txt")));
      var waiting = service.blockingStub().describeWorkflowExecution(
          DescribeWorkflowExecutionRequest.newBuilder()
              .setNamespace("default").setExecution(execution).build());
      assertEquals(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING,
          waiting.getWorkflowExecutionInfo().getStatus());
      System.out.println("[demo] Timer fired while the Spring application was stopped");

      secondWorker = startWorker(address, "after-restart", audit, evidence.resolve("worker-after.log"));
      owned.add(secondWorker);
      awaitReady(secondWorker, evidence.resolve("worker-after.log"));
      DemoResult result = WorkflowStub.fromTyped(workflow)
          .getResult(60, TimeUnit.SECONDS, DemoResult.class);

      assertEquals(workflowId, result.workflowId());
      assertEquals("before-crash", result.startedBy());
      assertEquals(firstPid, result.startedProcessId());
      assertEquals("after-restart", result.completedBy());
      assertEquals(secondWorker.pid(), result.completedProcessId());
      assertNotEquals(result.startedProcessId(), result.completedProcessId());
      assertEquals(1, Files.readAllLines(audit.resolve("started.txt")).size(),
          "Replay must reuse the first activity's recorded result");
      assertEquals(1, Files.readAllLines(audit.resolve("completed.txt")).size());

      History completedHistory = history(service, execution);
      assertEquals(1, count(completedHistory, EventType.EVENT_TYPE_WORKFLOW_EXECUTION_STARTED));
      assertEquals(2, count(completedHistory, EventType.EVENT_TYPE_ACTIVITY_TASK_COMPLETED));
      assertEquals(1, count(completedHistory, EventType.EVENT_TYPE_WORKFLOW_EXECUTION_COMPLETED));
      var completed = service.blockingStub().describeWorkflowExecution(
          DescribeWorkflowExecutionRequest.newBuilder()
              .setNamespace("default").setExecution(execution).build());
      assertEquals(execution.getRunId(), completed.getWorkflowExecutionInfo().getExecution().getRunId());
      assertEquals(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED,
          completed.getWorkflowExecutionInfo().getStatus());
      Files.writeString(evidence.resolve("history-after.txt"), completedHistory.toString());

      String summary = "PASS\n"
          + "Workflow " + workflowId + "\n"
          + "Same run " + execution.getRunId() + "\n"
          + "Killed worker process " + firstPid + "\n"
          + "Restarted worker process " + secondWorker.pid() + "\n"
          + "Timer fired while the Spring worker was stopped\n"
          + "Start activity ran once\n"
          + "Completion activity ran once, in the restarted worker\n"
          + "Temporal development server remained alive in a separate process\n"
          + "Normal worker cache settings used\n";
      Files.writeString(evidence.resolve("summary.txt"), summary);
      System.out.println("[demo] PASS - same workflow and run completed in a new Spring process");
      System.out.println("[demo] Evidence saved to " + evidence);
    } catch (Throwable error) {
      failure = error;
      throw error;
    } finally {
      try {
        stopProcesses(firstWorker, secondWorker, server);
      } catch (IllegalStateException | InterruptedException cleanupError) {
        // Keep the test's own failure as the reported cause; attach the cleanup problem to it.
        if (failure == null) {
          throw cleanupError;
        }
        failure.addSuppressed(cleanupError);
      } finally {
        if (service != null) {
          service.shutdownNow();
          service.awaitTermination(5, TimeUnit.SECONDS);
        }
        Runtime.getRuntime().removeShutdownHook(killOnExit);
        System.out.println("[demo] Worker logs and history are in " + evidence);
      }
    }
  }

  private static int freeLoopbackPort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  private static Process startServer(int port, Path evidence) throws Exception {
    // Append, so a failed first attempt's output stays in the log beside the successful one.
    return new ProcessBuilder(System.getProperty("temporal.cli", "temporal"),
        "server", "start-dev", "--ip", "127.0.0.1", "--port", Integer.toString(port),
        "--headless", "--db-filename", evidence.resolve("temporal.db").toString())
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(evidence.resolve("temporal.log").toFile()))
        .start();
  }

  private static Process startWorker(String address, String name, Path audit, Path log)
      throws Exception {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    Path jar = Path.of("target", "temporal-crash-demo.jar").toAbsolutePath();
    return new ProcessBuilder(java, "-jar", jar.toString(),
        "--spring.temporal.connection.target=" + address,
        "--demo.worker-name=" + name,
        "--demo.audit-directory=" + audit)
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
  }

  private static void awaitReady(Process worker, Path log) throws Exception {
    await("Spring worker readiness", Duration.ofSeconds(30), () -> {
      if (!worker.isAlive()) {
        throw new IllegalStateException("Spring worker exited. Log follows.\n" + Files.readString(log));
      }
      return Files.readString(log).contains("WORKER_READY");
    });
  }

  private static History history(WorkflowServiceStubs service, WorkflowExecution execution) {
    return service.blockingStub().getWorkflowExecutionHistory(
        GetWorkflowExecutionHistoryRequest.newBuilder()
            .setNamespace("default").setExecution(execution).build()).getHistory();
  }

  private static long count(History history, EventType type) {
    return history.getEventsList().stream().filter(event -> event.getEventType() == type).count();
  }

  private static boolean hasEvent(History history, EventType type) {
    return count(history, type) > 0;
  }

  private static void await(String description, Duration timeout, Callable<Boolean> condition)
      throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      if (condition.call()) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Timed out waiting for " + description);
  }

  private static void stopProcesses(Process... processes) throws InterruptedException {
    // Signal every owned process before waiting, including when the test fails partway.
    for (Process process : processes) {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
    for (Process process : processes) {
      if (process != null && !process.waitFor(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Cannot stop owned process " + process.pid());
      }
    }
  }
}
