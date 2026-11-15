package com.example.temporal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("demoActivities")
public class DemoActivitiesImpl implements DemoActivities {
  private final Path auditDirectory;
  private final String workerName;

  public DemoActivitiesImpl(
      @Value("${demo.audit-directory}") String auditDirectory,
      @Value("${demo.worker-name}") String workerName) throws IOException {
    this.auditDirectory = Path.of(auditDirectory);
    this.workerName = workerName;
    Files.createDirectories(this.auditDirectory);
  }

  @Override
  public StartReceipt recordStart(String workflowId) {
    append("started.txt", workflowId);
    return new StartReceipt(workflowId, workerName, ProcessHandle.current().pid());
  }

  @Override
  public DemoResult recordCompletion(StartReceipt receipt) {
    append("completed.txt", receipt.workflowId());
    return new DemoResult(
        receipt.workflowId(), receipt.workerName(), receipt.processId(),
        workerName, ProcessHandle.current().pid());
  }

  private synchronized void append(String filename, String workflowId) {
    Path file = auditDirectory.resolve(filename);
    // ponytail: append-only evidence for this demo; real effects need duplicate-request handling.
    String line = workflowId + " " + workerName + " " + ProcessHandle.current().pid() + "\n";
    try {
      Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot record workflow " + workflowId + " in " + file, error);
    }
  }
}
