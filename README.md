# Temporal crash and restart demo

This project force-kills a Spring Boot worker after Temporal has saved an activity result and a timer.
A fresh Spring process then completes the same workflow execution. The test checks both the workflow
ID and the run ID, and counts activity calls recorded in files.

## Run it

Install Java 21 or newer, Maven 3.9 or newer, and Temporal CLI 1.10.0. Put `java`, `mvn` and
`temporal` on your command search path. Check that `mvn -version` reports Java 21 or newer and
`temporal --version` reports 1.10.0. The first build downloads dependencies from Maven Central.

Download the archive for your operating system from the official Temporal release page and extract
its executable into a directory on your command search path.
[Temporal CLI 1.10.0 downloads](https://github.com/temporalio/cli/releases/tag/v1.10.0)

From this directory, run the following command on macOS or Linux.

```bash
./run-demo.sh
```

On Windows, or from your development environment, run the equivalent Maven command.

```bash
mvn verify
```

The build runs two workflow tests, three activity recording tests and one live process restart test.
`mvn test` runs only the first five. The live test waits for a real ten-second timer.

The live test starts workers from `target/temporal-crash-demo.jar`. If you run `CrashRestartIT` from
your development environment, run `mvn package -DskipTests` first, or it uses an old jar or none. A successful run ends with `BUILD SUCCESS`.

## What happens

```text
Maven starts Temporal's development server in its own process
  Temporal saves history in a local SQLite database
       |
       +--- Spring worker A
       |      Record start activity
       |      Temporal saves its result and starts a timer
       |      Test force-kills worker A
       |
       |    Timer fires while Spring is stopped
       |
       +--- Spring worker B
              Replay receives the saved start result
              Record completion activity
              Same workflow and run finish
```

The test waits until Temporal's history contains `TimerStarted` before killing the worker. This also
proves that Temporal has recorded the preceding activity's successful completion. It waits for
`TimerFired` while the worker is down, then starts a fresh worker on the same task queue.

Both workers are separate operating-system processes running the packaged Spring Boot application.
`Process.destroyForcibly()` kills the first process abruptly. The live test uses the real activity
implementation, network connections to the development server, and files that record each activity call.
It checks that the start activity runs once and the completion activity runs in the new process.

The live test uses the normal worker cache settings. Temporal reassigns a task when the dead worker
fails to pick it up within the timeout. The restarted worker rebuilds workflow state from history.

The test starts an isolated Temporal server on an available local port. It creates a new SQLite
database under its evidence directory and stops its own processes when it finishes or fails. The
server stays alive throughout the Spring restart. Server recovery and lost database storage need
separate tests. The two faster workflow tests use fake activities and automatic time skipping.

## Evidence from each run

Open the new directory under `target/crash-restart-evidence/`.

| File | Contents |
| --- | --- |
| `summary.txt` | The passing result, workflow ID, run ID and both process IDs |
| `worker-before.log` | First Spring worker's startup and execution logs |
| `worker-after.log` | Restarted worker's logs |
| `temporal.log` | Development server's startup and runtime logs |
| `temporal.db` | Local database containing this test's Temporal state |
| `history-before.txt` | Temporal history saved before the forced crash |
| `history-after.txt` | Completed history from the same run |
| `activity-calls/started.txt` | One line for each start activity invocation |
| `activity-calls/completed.txt` | One line for each completion activity invocation |

A failed run leaves its available logs in that directory. `summary.txt` appears only after every
recovery check passes. Maven also writes test reports under `target/surefire-reports/` and
`target/failsafe-reports/`.

## Code to read

| File | Purpose |
| --- | --- |
| `src/main/resources/application.yml` | Connects Spring to Temporal and registers the worker |
| `DemoWorkflowImpl.java` | Calls the first activity, waits, then calls the second activity |
| `DemoActivitiesImpl.java` | Appends evidence to files and returns the worker's identity |
| `DemoApplication.java` | Starts Spring and confirms that its Temporal worker started |
| `CrashRestartIT.java` | Starts the server and worker processes, kills one and checks recovery |
| `DemoWorkflowTest.java` | Checks receipt handling and a permanent activity failure |
| `ActivityRecordingTest.java` | Checks repeated calls and recording failures |

Production Java files are in `src/main/java/com/example/temporal/`. Test files are in
`src/test/java/com/example/temporal/`.

The queue name is `crash-demo` in both the worker configuration and the client that starts the
workflow. Spring creates the `demoActivities` bean. Temporal creates workflow instances.

The activity appends every invocation to the evidence files. This makes a repeated call visible.
For a real payment or email, add handling for repeated requests. A crash after an external effect but
before Temporal records its completion can cause another activity attempt.

## Versions

Java 21 or newer (the build targets 21; verified with 25), Spring Boot 4.1.2, Temporal Java libraries 1.40.0 and Temporal CLI 1.10.0, which includes
Temporal Server 1.32.2. The Spring Boot parent fixes the versions of its managed dependencies and
build plugins. The Temporal dependencies and extra test plugin are pinned in `pom.xml`.

## Sources

- [Temporal Spring Boot integration](https://docs.temporal.io/develop/java/integrations/spring-boot-integration)
- [Temporal Java testing](https://docs.temporal.io/develop/java/best-practices/testing-suite)
- [Temporal development server](https://docs.temporal.io/cli/command-reference/server)
- [Workflow replay](https://docs.temporal.io/workflows)
- [Activity execution](https://docs.temporal.io/activity-definition)

## Verified run

`verified-run.txt` contains the result of a successful run on macOS with Java 25.0.5. All six tests
passed, including the live crash-and-restart check. Linux and Windows runs remain unverified.
