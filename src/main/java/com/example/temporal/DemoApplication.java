package com.example.temporal;

import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
  private static final Logger LOG = LoggerFactory.getLogger(DemoApplication.class);

  public static void main(String[] args) {
    var context = SpringApplication.run(DemoApplication.class, args);
    if (!context.getBean(WorkerFactory.class).isStarted()) {
      context.close();
      throw new IllegalStateException("Temporal worker did not start");
    }
    LOG.info("WORKER_READY process={}", ProcessHandle.current().pid());
  }
}
