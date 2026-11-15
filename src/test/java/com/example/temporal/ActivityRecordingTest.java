package com.example.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActivityRecordingTest {
  @TempDir
  Path directory;

  @Test
  void shouldKeepEachActivityInvocationVisibleIncludingRepeatedCalls() throws Exception {
    var activities = new DemoActivitiesImpl(directory.toString(), "worker");

    activities.recordStart("order-42");
    activities.recordStart("order-42");

    assertEquals(2, Files.readAllLines(directory.resolve("started.txt")).size());
  }

  @Test
  void shouldReportTheWorkflowAndPathWhenRecordingFails() throws Exception {
    var activities = new DemoActivitiesImpl(directory.toString(), "worker");
    Files.createDirectory(directory.resolve("started.txt"));

    UncheckedIOException error = assertThrows(UncheckedIOException.class,
        () -> activities.recordStart("order-42"));

    assertTrue(error.getMessage().contains("order-42"));
    assertTrue(error.getMessage().contains("started.txt"));
  }

  @Test
  void shouldRejectAnAuditDirectoryThatIsAlreadyAFile() throws Exception {
    Path file = Files.writeString(directory.resolve("existing-file"), "data");

    assertThrows(IOException.class, () -> new DemoActivitiesImpl(file.toString(), "worker"));
  }
}
