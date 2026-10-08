package izumi.distage.sbt;

import sbt.testing.Event;
import sbt.testing.EventHandler;
import sbt.testing.Fingerprint;
import sbt.testing.Framework;
import sbt.testing.Logger;
import sbt.testing.OptionalThrowable;
import sbt.testing.Runner;
import sbt.testing.Selector;
import sbt.testing.Status;
import sbt.testing.SubclassFingerprint;
import sbt.testing.Task;
import sbt.testing.TaskDef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SdkFixtures {
    private SdkFixtures() {}

    public static SubclassFingerprint subclass(boolean module, String superclass, boolean constructor) {
        return new SubclassFingerprint() {
            @Override public boolean isModule() { return module; }
            @Override public String superclassName() { return superclass; }
            @Override public boolean requireNoArgConstructor() { return constructor; }
        };
    }

    public static Event event(String name, Fingerprint marker, Selector selected, Status result, OptionalThrowable cause, long elapsed) {
        return new Event() {
            @Override public String fullyQualifiedName() { return name; }
            @Override public Fingerprint fingerprint() { return marker; }
            @Override public Selector selector() { return selected; }
            @Override public Status status() { return result; }
            @Override public OptionalThrowable throwable() { return cause; }
            @Override public long duration() { return elapsed; }
        };
    }

    public static Task task(TaskDef definition, String[] tags, Function<EventHandler, Task[]> execute) {
        return new Task() {
            @Override public TaskDef taskDef() { return definition; }
            @Override public String[] tags() { return tags.clone(); }
            @Override public Task[] execute(EventHandler handler, Logger[] loggers) { return execute.apply(handler); }
        };
    }

    @FunctionalInterface
    public interface RunnerFactory {
        Runner create(String[] arguments, String[] remoteArguments, ClassLoader loader);
    }

    public static Framework framework(String name, Fingerprint marker, RunnerFactory create) {
        return new Framework() {
            @Override public String name() { return name; }
            @Override public Fingerprint[] fingerprints() { return new Fingerprint[]{marker}; }
            @Override public Runner runner(String[] arguments, String[] remote, ClassLoader loader) { return create.create(arguments, remote, loader); }
        };
    }

    public static Runner runner(String[] arguments, String[] remoteArguments, Function<TaskDef[], Task[]> tasks, Supplier<String> done) {
        String[] capturedArguments = arguments.clone();
        String[] capturedRemote = remoteArguments.clone();
        return new Runner() {
            @Override public String[] args() { return capturedArguments.clone(); }
            @Override public String[] remoteArgs() { return capturedRemote.clone(); }
            @Override public String done() { return done.get(); }
            @Override public Task[] tasks(TaskDef[] definitions) { return tasks.apply(definitions); }
        };
    }

    public static void deleteTree(Path directory) throws IOException {
        try (var entries = Files.walk(directory)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
