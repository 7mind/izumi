package izumi.distage.sbt.target;

import sbt.testing.Event;
import sbt.testing.EventHandler;
import sbt.testing.Fingerprint;
import sbt.testing.Logger;
import sbt.testing.OptionalThrowable;
import sbt.testing.Selector;
import sbt.testing.Status;
import sbt.testing.SubclassFingerprint;
import sbt.testing.SuiteSelector;
import sbt.testing.Task;
import sbt.testing.TaskDef;
import sbt.testing.TestSelector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class TaskCompletenessTest {
    private static final Logger[] NO_LOGGERS = new Logger[0];
    private static final Task[] NO_CHILDREN = new Task[0];
    private static final int CONCURRENT_EVENTS = 4;
    private static final long JOIN_MILLIS = 10000L;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("Task completion checks require a new directory");
        Path directory = Path.of(arguments[0]).toAbsolutePath();
        Files.createDirectory(directory);
        run("memory", name -> new MemoryStore());
        run("file", name -> {
            try { return new TaskCompleteness.FileCompletionStore(Files.createDirectory(directory.resolve(name))); }
            catch (Exception cause) { throw new IllegalStateException(cause); }
        });
        try (var entries = Files.walk(directory)) {
            for (Path path : entries.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private interface StoreFactory { TaskCompleteness.CompletionStore create(String name); }
    private interface Execute { Task[] run(EventHandler handler); }

    private static void run(String mode, StoreFactory factory) throws Exception {
        TaskDef left = definition("fixture.Left", true);
        TaskDef missing = definition("fixture.Missing", true);
        TaskDef right = definition("fixture.Right", true);
        TaskCompleteness.CompletionStore store = factory.create("omission");
        Event leftEvent = event(left, Status.Success);
        Event rightEvent = event(right, Status.Success);
        Task[] tasks = TaskCompleteness.normalise(new TaskDef[]{left, missing, right}, new Task[]{task(left, h -> { h.handle(leftEvent); return NO_CHILDREN; }), task(right, h -> { h.handle(rightEvent); return NO_CHILDREN; })}, store);
        List<Event> delivered = new ArrayList<>();
        for (Task task : tasks) task.execute(delivered::add, NO_LOGGERS);
        require(tasks.length == 3 && delivered.size() == 3 && delivered.get(0) == leftEvent && delivered.get(1) == rightEvent, "Original tasks/events changed");
        require(delivered.get(2).fullyQualifiedName().equals(missing.fullyQualifiedName()) && delivered.get(2).status() == Status.Error && delivered.get(2).selector() instanceof SuiteSelector, "Missing suite did not retain a terminal error");
        require(delivered.get(2).throwable().get().getMessage().contains("terminal"), "Missing suite lost its diagnosis");
        require(store.completed().size() == 3 && store.completed().stream().allMatch(TaskCompleteness.Completion::returnedNormally), "Selected tasks have no target terminal records");
        TaskCompleteness.Completion absent = store.completed().stream().filter(c -> c.suite().value().equals(missing.fullyQualifiedName())).findFirst().orElseThrow();
        require(absent.counts().error() == 1 && absent.counts().success() == 0, "Missing suite counts differ");
        rejects(() -> store.publish(absent));
        ok(mode, "missing suite error, original event identity and one terminal per task");

        TaskCompleteness.CompletionStore foreignStore = factory.create("foreign");
        TaskDef foreign = definition("fixture.Foreign", false);
        Task[] original = new Task[]{task(foreign, h -> NO_CHILDREN)};
        require(TaskCompleteness.normalise(new TaskDef[]{foreign}, original, foreignStore) == original && foreignStore.completed().isEmpty(), "Foreign framework tasks were changed");
        ok(mode, "foreign array identity");

        TaskCompleteness.CompletionStore statusStore = factory.create("statuses");
        List<Event> statuses = new ArrayList<>();
        for (Status status : Status.values()) statuses.add(event(left, status));
        Task statusTask = task(left, h -> { statuses.forEach(h::handle); return NO_CHILDREN; });
        List<Event> observed = new ArrayList<>();
        TaskCompleteness.normalise(new TaskDef[]{left}, new Task[]{statusTask}, statusStore)[0].execute(observed::add, NO_LOGGERS);
        require(observed.equals(statuses), "Status payload identities changed");
        require(statusStore.completed().get(0).counts().equals(new TaskCompleteness.Counts(1,1,1,1,1,1,1)), "Terminal statuses were conflated");
        ok(mode, "all status identities and counts");

        TaskCompleteness.CompletionStore childStore = factory.create("children");
        Task child = task(left, h -> { h.handle(leftEvent); return NO_CHILDREN; });
        Task root = task(left, h -> { h.handle(leftEvent); return new Task[]{child, child}; });
        Task acknowledged = TaskCompleteness.normalise(new TaskDef[]{left}, new Task[]{root}, childStore)[0];
        Task[] children = acknowledged.execute(e -> {}, NO_LOGGERS);
        require(childStore.completed().isEmpty(), "Root returned before its descendants completed");
        children[0].execute(e -> {}, NO_LOGGERS);
        require(childStore.completed().isEmpty(), "First child published an incomplete root");
        children[1].execute(e -> {}, NO_LOGGERS);
        require(childStore.completed().size() == 1 && childStore.completed().get(0).counts().success() == 3, "Descendant event counts differ");
        ok(mode, "terminal publication after the complete descendant graph");

        TaskCompleteness.CompletionStore lateStore = factory.create("late");
        AtomicReference<EventHandler> saved = new AtomicReference<>();
        Task late = task(left, h -> { saved.set(h); h.handle(leftEvent); return NO_CHILDREN; });
        TaskCompleteness.normalise(new TaskDef[]{left}, new Task[]{late}, lateStore)[0].execute(e -> {}, NO_LOGGERS);
        rejects(() -> saved.get().handle(leftEvent));
        require(lateStore.completed().get(0).counts().success() == 1, "Late delivery changed the terminal record");
        ok(mode, "late callback rejection");

        TaskCompleteness.CompletionStore failedStore = factory.create("callback");
        RuntimeException sentinel = new RuntimeException("original callback failure");
        Task failing = TaskCompleteness.normalise(new TaskDef[]{left}, new Task[]{task(left, h -> { h.handle(leftEvent); return NO_CHILDREN; })}, failedStore)[0];
        try { failing.execute(e -> { throw sentinel; }, NO_LOGGERS); throw new AssertionError("Callback did not fail"); }
        catch (RuntimeException cause) { require(cause == sentinel, "Original callback throwable was replaced"); }
        require(!failedStore.completed().get(0).returnedNormally() && failedStore.completed().get(0).counts().success() == 0, "Callback failure was a successful terminal");
        ok(mode, "callback throwable identity and failed terminal");

        TaskCompleteness.CompletionStore concurrentStore = factory.create("concurrent");
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        Task concurrent = task(left, h -> {
            List<Thread> threads = new ArrayList<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            for (int index = 0; index < CONCURRENT_EVENTS; index++) threads.add(new Thread(() -> { try { h.handle(leftEvent); } catch (Throwable cause) { failure.compareAndSet(null,cause); } }));
            threads.forEach(Thread::start);
            for (Thread thread : threads) {
                try { thread.join(JOIN_MILLIS); }
                catch (InterruptedException cause) { throw new IllegalStateException(cause); }
                require(!thread.isAlive(), "Concurrent callback did not return");
            }
            require(failure.get() == null, "Concurrent callback failed");
            return NO_CHILDREN;
        });
        TaskCompleteness.normalise(new TaskDef[]{left}, new Task[]{concurrent}, concurrentStore)[0].execute(e -> {
            int entered = active.incrementAndGet();
            maximum.accumulateAndGet(entered, Math::max);
            try { Thread.sleep(10L); }
            catch (InterruptedException cause) { throw new IllegalStateException(cause); }
            finally { active.decrementAndGet(); }
        }, NO_LOGGERS);
        require(maximum.get() == 1 && concurrentStore.completed().get(0).counts().success() == CONCURRENT_EVENTS, "Callbacks overlapped or were lost");
        ok(mode, "serialized concurrent callbacks with exact counts");
    }

    private static final class MemoryStore implements TaskCompleteness.CompletionStore {
        private final Map<UUID, TaskCompleteness.Completion> records = new LinkedHashMap<>();
        @Override public synchronized void publish(TaskCompleteness.Completion value) {
            if (records.putIfAbsent(value.token(),value) != null) throw new IllegalStateException("Duplicate target terminal record");
        }
        @Override public synchronized List<TaskCompleteness.Completion> completed() { return List.copyOf(records.values()); }
    }

    private static TaskDef definition(String name, boolean owned) {
        return new TaskDef(name, new FingerprintValue(owned), false, new Selector[]{new SuiteSelector()});
    }

    private static final class FingerprintValue implements SubclassFingerprint {
        private final boolean owned;
        private FingerprintValue(boolean owned) { this.owned = owned; }
        @Override public boolean isModule() { return false; }
        @Override public String superclassName() { return owned ? "izumi.distage.testkit.runner.TestSuite" : "fixture.ForeignSuperclass"; }
        @Override public boolean requireNoArgConstructor() { return true; }
    }

    private static Task task(TaskDef definition, Execute execute) {
        return new Task() {
            @Override public TaskDef taskDef() { return definition; }
            @Override public String[] tags() { return new String[]{"fixture"}; }
            @Override public Task[] execute(EventHandler handler, Logger[] loggers) { return execute.run(handler); }
        };
    }

    private static Event event(TaskDef definition, Status status) {
        return new Event() {
            @Override public String fullyQualifiedName() { return definition.fullyQualifiedName(); }
            @Override public Fingerprint fingerprint() { return definition.fingerprint(); }
            @Override public Selector selector() { return new TestSelector("fixture " + status); }
            @Override public Status status() { return status; }
            @Override public OptionalThrowable throwable() { return status == Status.Error || status == Status.Failure ? new OptionalThrowable(new IllegalStateException(status.toString())) : new OptionalThrowable(); }
            @Override public long duration() { return 13L; }
        };
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected invariant failure"); }
        catch (IllegalStateException expected) { }
    }
    private static void ok(String mode, String name) { System.out.println("TASK_COMPLETENESS_CHECK_OK " + mode + " " + name); }
}
