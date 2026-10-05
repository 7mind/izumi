package izumi.distage.sbt.target;

import sbt.testing.Event;
import sbt.testing.EventHandler;
import sbt.testing.Fingerprint;
import sbt.testing.Logger;
import sbt.testing.OptionalThrowable;
import sbt.testing.Runner;
import sbt.testing.Selector;
import sbt.testing.Status;
import sbt.testing.SubclassFingerprint;
import sbt.testing.SuiteSelector;
import sbt.testing.Task;
import sbt.testing.TaskDef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class TaskCompleteness {
    private static final String SUITE_SUPERCLASS = "izumi.distage.testkit.runner.TestSuite";
    private static final String FILE_PREFIX = "target-";
    private static final String FILE_SUFFIX = ".terminal";
    private static final String SCHEMA_VERSION = "1";
    private static final String RUNNER_TASKS_DESCRIPTOR = "([Lsbt/testing/TaskDef;)[Lsbt/testing/Task;";

    private TaskCompleteness() {}

    public static boolean isOutermostRunnerTasks() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
            .filter(frame -> frame.getMethodName().equals("tasks") && frame.getDescriptor().equals(RUNNER_TASKS_DESCRIPTOR) && Runner.class.isAssignableFrom(frame.getDeclaringClass()))
            .limit(2).count() == 1);
    }

    public record SuiteName(String value) {
        public SuiteName {
            Objects.requireNonNull(value, "Missing target suite identity");
            if (value.isEmpty() || value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
                throw new IllegalArgumentException("Invalid target suite identity");
            }
        }
    }

    public record Counts(int success, int failure, int error, int skipped, int ignored, int canceled, int pending) {
        public Counts {
            if (success < 0 || failure < 0 || error < 0 || skipped < 0 || ignored < 0 || canceled < 0 || pending < 0) throw new IllegalArgumentException("Negative target terminal count");
        }
    }

    public record Completion(UUID token, SuiteName suite, long pid, boolean returnedNormally, Counts counts) {
        public Completion {
            Objects.requireNonNull(token, "Missing target terminal token");
            Objects.requireNonNull(suite, "Missing target terminal suite");
            Objects.requireNonNull(counts, "Missing target terminal counts");
            if (pid <= 0) throw new IllegalArgumentException("Invalid target terminal process");
        }
    }

    public interface CompletionSink {
        void publish(Completion completion);
    }

    public interface CompletionStore extends CompletionSink {
        List<Completion> completed();
    }

    public static final class FileCompletionStore implements CompletionStore {
        private final Path directory;

        public FileCompletionStore(Path directory) {
            this.directory = Objects.requireNonNull(directory, "Missing target terminal directory");
            if (!directory.isAbsolute() || !Files.isDirectory(directory)) throw new IllegalArgumentException("Target terminal directory must be an existing absolute path");
        }

        @Override
        public void publish(Completion value) {
            Path destination = directory.resolve(FILE_PREFIX + value.token() + FILE_SUFFIX);
            if (Files.exists(destination)) throw new IllegalStateException("Duplicate target terminal record");
            Counts counts = value.counts();
            String line = SCHEMA_VERSION + "\t" + value.token() + "\t" + value.suite().value() + "\t" + value.pid() + "\t" + value.returnedNormally()
                + "\t" + counts.success() + "\t" + counts.failure() + "\t" + counts.error() + "\t" + counts.skipped() + "\t" + counts.ignored() + "\t" + counts.canceled() + "\t" + counts.pending();
            try {
                Path temporary = Files.createTempFile(directory, "target-publication-", ".tmp");
                try {
                    Files.writeString(temporary, line, StandardCharsets.UTF_8);
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary); }
            } catch (IOException cause) { throw new IllegalStateException("Cannot publish target terminal record", cause); }
        }

        @Override
        public List<Completion> completed() {
            List<Completion> records = new ArrayList<>();
            try (var entries = Files.list(directory)) {
                for (Path path : entries.filter(value -> value.getFileName().toString().endsWith(FILE_SUFFIX)).toList()) {
                    String[] fields = Files.readString(path, StandardCharsets.UTF_8).split("\t", -1);
                    if (fields.length != 12 || !fields[0].equals(SCHEMA_VERSION) || !(fields[4].equals("true") || fields[4].equals("false"))) throw new IllegalArgumentException("Malformed target terminal record: " + path);
                    UUID token = UUID.fromString(fields[1]);
                    if (!path.getFileName().toString().equals(FILE_PREFIX + token + FILE_SUFFIX)) throw new IllegalArgumentException("Target terminal filename differs from its token");
                    records.add(new Completion(token, new SuiteName(fields[2]), Long.parseLong(fields[3]), Boolean.parseBoolean(fields[4]),
                        new Counts(Integer.parseInt(fields[5]), Integer.parseInt(fields[6]), Integer.parseInt(fields[7]), Integer.parseInt(fields[8]), Integer.parseInt(fields[9]), Integer.parseInt(fields[10]), Integer.parseInt(fields[11]))));
                }
            } catch (IOException cause) { throw new IllegalStateException("Cannot read target terminal records", cause); }
            return List.copyOf(records);
        }
    }

    public static Task[] normalise(TaskDef[] definitions, Task[] tasks, CompletionSink sink) {
        Objects.requireNonNull(definitions, "Missing selected task definitions");
        Objects.requireNonNull(tasks, "Runner returned no task array");
        Objects.requireNonNull(sink, "Missing target completion sink");
        boolean owned = false;
        for (TaskDef definition : definitions) owned |= isOwned(definition);
        if (!owned) return tasks;
        Set<SuiteName> returned = new HashSet<>();
        List<Task> completed = new ArrayList<>();
        for (Task task : tasks) {
            if (isOwned(task.taskDef())) {
                SuiteName name = new SuiteName(task.taskDef().fullyQualifiedName());
                returned.add(name);
                completed.add(task instanceof AcknowledgedTask ? task : new AcknowledgedTask(task, new CompletionScope(name, sink)));
            } else completed.add(task);
        }
        for (TaskDef definition : definitions) {
            SuiteName name = new SuiteName(definition.fullyQualifiedName());
            if (isOwned(definition) && !returned.contains(name)) completed.add(new AcknowledgedTask(new MissingTask(definition), new CompletionScope(name, sink)));
        }
        return completed.toArray(Task[]::new);
    }

    private static boolean isOwned(TaskDef definition) {
        Objects.requireNonNull(definition, "Missing task definition");
        return definition.fingerprint() instanceof SubclassFingerprint fingerprint && !fingerprint.isModule() && fingerprint.superclassName().equals(SUITE_SUPERCLASS);
    }

    private static final class CompletionScope {
        private final SuiteName suite;
        private final CompletionSink sink;
        private final UUID token = UUID.randomUUID();
        private final EnumMap<Status, Integer> counts = new EnumMap<>(Status.class);
        private int remaining = 1;
        private boolean returnedNormally = true;

        private CompletionScope(SuiteName suite, CompletionSink sink) {
            this.suite = suite;
            this.sink = sink;
            for (Status status : Status.values()) counts.put(status, 0);
        }

        private synchronized void record(Status status) {
            if (remaining <= 0) throw new IllegalStateException("Event emitted after target suite terminal");
            counts.put(status, Math.addExact(counts.get(status), 1));
        }

        private synchronized Task[] children(Task[] tasks) {
            Objects.requireNonNull(tasks, "Task returned no child task array");
            remaining = Math.addExact(remaining, tasks.length);
            Task[] children = new Task[tasks.length];
            for (int index = 0; index < tasks.length; index++) children[index] = new AcknowledgedTask(tasks[index], this);
            return children;
        }

        private synchronized void failed() { returnedNormally = false; }

        private synchronized void finish() {
            if (remaining <= 0) throw new IllegalStateException("Target suite completed twice");
            remaining -= 1;
            if (remaining == 0) sink.publish(new Completion(token, suite, ProcessHandle.current().pid(), returnedNormally,
                new Counts(counts.get(Status.Success), counts.get(Status.Failure), counts.get(Status.Error), counts.get(Status.Skipped), counts.get(Status.Ignored), counts.get(Status.Canceled), counts.get(Status.Pending))));
        }
    }

    private static final class AcknowledgedTask implements Task {
        private final Task delegate;
        private final CompletionScope completion;
        private boolean executed;

        private AcknowledgedTask(Task delegate, CompletionScope completion) {
            this.delegate = Objects.requireNonNull(delegate, "Missing target task");
            this.completion = completion;
        }

        @Override public TaskDef taskDef() { return delegate.taskDef(); }
        @Override public String[] tags() { return delegate.tags(); }

        @Override
        public Task[] execute(EventHandler handler, Logger[] loggers) {
            synchronized (this) {
                if (executed) throw new IllegalStateException("Target task executed twice");
                executed = true;
            }
            CountingHandler counter = new CountingHandler(handler, completion);
            Throwable original = null;
            boolean projected = false;
            try { return completion.children(delegate.execute(counter, loggers)); }
            catch (Throwable cause) {
                original = cause;
                completion.failed();
                if (counter.callbackFailed()) throw cause;
                try { counter.handle(new FailureEvent(delegate.taskDef(), cause)); }
                catch (Throwable delivery) { cause.addSuppressed(delivery); throw cause; }
                projected = true;
                return new Task[0];
            }
            finally {
                counter.close();
                try { completion.finish(); }
                catch (Throwable publication) {
                    if (original == null) throw publication;
                    if (projected) { publication.addSuppressed(original); throw publication; }
                    original.addSuppressed(publication);
                }
            }
        }
    }

    private static final class CountingHandler implements EventHandler {
        private final EventHandler delegate;
        private final CompletionScope completion;
        private boolean closed;
        private boolean callbackFailed;

        private CountingHandler(EventHandler delegate, CompletionScope completion) {
            this.delegate = Objects.requireNonNull(delegate, "Missing target event handler");
            this.completion = completion;
        }

        @Override
        public synchronized void handle(Event event) {
            if (closed) throw new IllegalStateException("Event emitted after target task terminal");
            try {
                delegate.handle(event);
                completion.record(event.status());
            } catch (Throwable cause) { callbackFailed = true; throw cause; }
        }

        private synchronized boolean callbackFailed() { return callbackFailed; }
        private synchronized void close() { closed = true; }
    }

    private static final class MissingTask implements Task {
        private final TaskDef definition;
        private MissingTask(TaskDef definition) { this.definition = definition; }
        @Override public TaskDef taskDef() { return definition; }
        @Override public String[] tags() { return new String[0]; }
        @Override public Task[] execute(EventHandler handler, Logger[] loggers) {
            handler.handle(new FailureEvent(definition, new IllegalStateException("Selected target suite has no terminal task: " + definition.fullyQualifiedName())));
            return new Task[0];
        }
    }

    private static final class FailureEvent implements Event {
        private final TaskDef definition;
        private final OptionalThrowable failure;
        private FailureEvent(TaskDef definition, Throwable cause) {
            this.definition = definition;
            this.failure = new OptionalThrowable(cause);
        }
        @Override public String fullyQualifiedName() { return definition.fullyQualifiedName(); }
        @Override public Fingerprint fingerprint() { return definition.fingerprint(); }
        @Override public Selector selector() { return new SuiteSelector(); }
        @Override public Status status() { return Status.Error; }
        @Override public OptionalThrowable throwable() { return failure; }
        @Override public long duration() { return 0L; }
    }
}
