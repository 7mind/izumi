package izumi.distage.sbt.target;

import sbt.testing.AnnotatedFingerprint;
import sbt.testing.Event;
import sbt.testing.EventHandler;
import sbt.testing.Fingerprint;
import sbt.testing.Logger;
import sbt.testing.NestedSuiteSelector;
import sbt.testing.NestedTestSelector;
import sbt.testing.OptionalThrowable;
import sbt.testing.Selector;
import sbt.testing.Status;
import sbt.testing.SubclassFingerprint;
import sbt.testing.SuiteSelector;
import sbt.testing.Task;
import sbt.testing.TaskDef;
import sbt.testing.TestSelector;
import sbt.testing.TestWildcardSelector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ForeignRunReportsTest {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("Foreign report checks require a new directory");
        Path directory = Files.createDirectory(Path.of(arguments[0]).toAbsolutePath());
        for (String mode : List.of("memory", "file")) {
            ForeignRunReports.Store store = mode.equals("memory") ? new MemoryStore() : new ForeignRunReports.FileStore(Files.createDirectory(directory.resolve(mode)));
            contract(mode, store);
        }
        try (var entries = Files.walk(directory)) {
            for (Path path : entries.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private static void contract(String mode, ForeignRunReports.Store store) {
        Fingerprint subclass = new ForeignRunReports.SubclassSnapshot(false, "fixture.ForeignMarker", true);
        Fingerprint annotation = new ForeignRunReports.AnnotationSnapshot(true, "fixture.ForeignAnnotation");
        TaskDef parent = new TaskDef("fixture.Foreign", subclass, true, new Selector[]{new SuiteSelector()});
        TaskDef child = new TaskDef("fixture.Child", annotation, false, new Selector[]{new NestedSuiteSelector("nested")});
        List<Event> original = new ArrayList<>();
        Throwable failure = new IllegalStateException("foreign failure", new IllegalArgumentException("cause"));
        List<Selector> selectors = List.of(new SuiteSelector(), new TestSelector("test"), new NestedSuiteSelector("suite"), new NestedTestSelector("suite", "test"), new TestWildcardSelector("wild*"));
        for (Fingerprint fingerprint : List.of(subclass, annotation)) for (Selector selector : selectors) for (Status status : Status.values()) original.add(new Event() {
            @Override public String fullyQualifiedName() { return "fixture.EventName"; }
            @Override public Fingerprint fingerprint() { return fingerprint; }
            @Override public Selector selector() { return selector; }
            @Override public Status status() { return status; }
            @Override public OptionalThrowable throwable() { return status == Status.Failure ? new OptionalThrowable(failure) : new OptionalThrowable(); }
            @Override public long duration() { return 42L; }
        });
        AtomicInteger executions = new AtomicInteger();
        Task descendant = task(child, handler -> { executions.incrementAndGet(); original.forEach(handler::handle); return new Task[0]; });
        Task root = task(parent, handler -> { executions.incrementAndGet(); return new Task[]{descendant}; });
        Task protectedRoot = TaskCompleteness.protect(new Task[]{root})[0];
        List<Event> received = new ArrayList<>();
        Task[] children = TaskCompleteness.captureForeign(new Task[]{protectedRoot}, store)[0].execute(received::add, new Logger[0]);
        children[0].execute(received::add, new Logger[0]);
        require(executions.get() == 2 && received.equals(original), "Foreign execution or original event references changed");
        List<ForeignRunReports.Report> reports = store.completed();
        require(reports.size() == 2 && reports.stream().allMatch(value -> value.owner().value().equals(parent.fullyQualifiedName()) && value.pid() == ProcessHandle.current().pid()), "Foreign descendants lost admission ownership");
        ForeignRunReports.Report report = reports.stream().filter(value -> value.group().value().equals(child.fullyQualifiedName())).findFirst().orElseThrow();
        require(report.events().size() == original.size(), "Foreign event count changed");
        for (int index = 0; index < original.size(); index++) {
            Event expected = original.get(index);
            Event actual = report.events().get(index);
            require(actual.fullyQualifiedName().equals(expected.fullyQualifiedName()) && sameSelector(actual.selector(), expected.selector()) && actual.status() == expected.status() && actual.duration() == expected.duration(), "Foreign event fields changed");
            if (expected.fingerprint() instanceof SubclassFingerprint left) {
                SubclassFingerprint right = (SubclassFingerprint) actual.fingerprint();
                require(left.isModule() == right.isModule() && left.superclassName().equals(right.superclassName()) && left.requireNoArgConstructor() == right.requireNoArgConstructor(), "Foreign subclass fingerprint changed");
            } else {
                AnnotatedFingerprint left = (AnnotatedFingerprint) expected.fingerprint();
                AnnotatedFingerprint right = (AnnotatedFingerprint) actual.fingerprint();
                require(left.isModule() == right.isModule() && left.annotationName().equals(right.annotationName()), "Foreign annotation fingerprint changed");
            }
            require(actual.throwable().isDefined() == expected.throwable().isDefined(), "Foreign failure presence changed");
            if (actual.throwable().isDefined()) {
                require(actual.throwable().get().getMessage().equals("java.lang.IllegalStateException: foreign failure") && actual.throwable().get().getCause().getMessage().equals("java.lang.IllegalArgumentException: cause"), "Foreign failure chain changed");
                require(java.util.Arrays.equals(actual.throwable().get().getStackTrace(), failure.getStackTrace()), "Foreign failure stack changed");
            }
        }
        try { store.publish(report); throw new AssertionError("Duplicate foreign report accepted"); }
        catch (IllegalStateException expected) { }
        System.out.println("FOREIGN_REPORT_CHECK_OK " + mode + " events=" + original.size() + " descendant-ownership duplicate-rejection original-references");
    }

    private interface Execute { Task[] run(EventHandler handler); }
    private static boolean sameSelector(Selector actual, Selector expected) {
        if (expected instanceof SuiteSelector) return actual instanceof SuiteSelector;
        if (expected instanceof TestSelector left && actual instanceof TestSelector right) return left.testName().equals(right.testName());
        if (expected instanceof NestedSuiteSelector left && actual instanceof NestedSuiteSelector right) return left.suiteId().equals(right.suiteId());
        if (expected instanceof NestedTestSelector left && actual instanceof NestedTestSelector right) return left.suiteId().equals(right.suiteId()) && left.testName().equals(right.testName());
        if (expected instanceof TestWildcardSelector left && actual instanceof TestWildcardSelector right) return left.testWildcard().equals(right.testWildcard());
        throw new IllegalArgumentException("Unsupported selector contract: " + expected);
    }
    private static Task task(TaskDef definition, Execute body) { return new Task() {
        @Override public TaskDef taskDef() { return definition; }
        @Override public String[] tags() { return new String[]{"foreign"}; }
        @Override public Task[] execute(EventHandler handler, Logger[] loggers) { return body.run(handler); }
    }; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class MemoryStore implements ForeignRunReports.Store {
        private final Map<UUID, ForeignRunReports.Report> values = new LinkedHashMap<>();
        @Override public void publish(ForeignRunReports.Report value) {
            if (values.putIfAbsent(value.token(), value) != null) throw new IllegalStateException("Duplicate foreign report");
        }
        @Override public List<ForeignRunReports.Report> completed() { return List.copyOf(values.values()); }
    }
}
