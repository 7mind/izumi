package izumi.distage.sbt.target;

import sbt.testing.EventHandler;
import sbt.testing.Logger;
import sbt.testing.Task;
import sbt.testing.TaskDef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TaskGroups {
    public static final String OPTION = "--distage-task-groups";
    private static final String PREFIX = "group-";
    private static final String SUFFIX = ".mapping";
    private static final String VERSION = "1";

    private TaskGroups() {}

    public record Mapping(TaskCompleteness.SuiteName listener, TaskCompleteness.SuiteName result) {
        public Mapping {
            Objects.requireNonNull(listener, "Missing listener group");
            Objects.requireNonNull(result, "Missing result group");
        }
    }

    public interface Store {
        void publish(Mapping mapping);
        List<Mapping> mappings();
    }

    public static final class MemoryStore implements Store {
        private final List<Mapping> values = new ArrayList<>();
        @Override public synchronized void publish(Mapping mapping) { values.add(Objects.requireNonNull(mapping)); }
        @Override public synchronized List<Mapping> mappings() { return List.copyOf(values); }
    }

    public static final class FileStore implements Store {
        private final Path directory;
        public FileStore(Path directory) {
            this.directory = Objects.requireNonNull(directory);
            if (!directory.isAbsolute() || !Files.isDirectory(directory)) throw new IllegalArgumentException("Group directory must exist and be absolute");
        }
        @Override public void publish(Mapping mapping) {
            Path destination = directory.resolve(PREFIX + UUID.randomUUID() + SUFFIX);
            try {
                Path temporary = Files.createTempFile(directory, PREFIX, ".tmp");
                try {
                    Files.writeString(temporary, VERSION + "\t" + mapping.listener().value() + "\t" + mapping.result().value(), StandardCharsets.UTF_8);
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary); }
            } catch (IOException cause) { throw new IllegalStateException("Cannot publish task group mapping", cause); }
        }
        @Override public List<Mapping> mappings() {
            List<Mapping> values = new ArrayList<>();
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(path -> path.getFileName().toString().startsWith(PREFIX) && path.getFileName().toString().endsWith(SUFFIX)).toList()) {
                    String[] fields = Files.readString(file, StandardCharsets.UTF_8).split("\t", -1);
                    if (fields.length != 3 || !fields[0].equals(VERSION)) throw new IllegalArgumentException("Malformed task group mapping: " + file);
                    values.add(new Mapping(new TaskCompleteness.SuiteName(fields[1]), new TaskCompleteness.SuiteName(fields[2])));
                }
            } catch (IOException cause) { throw new IllegalStateException("Cannot read task group mappings", cause); }
            return List.copyOf(values);
        }
    }

    public record Invocation(String[] arguments, Path directory) {}

    public static Invocation parse(String[] arguments) {
        List<String> forwarded = new ArrayList<>();
        Path directory = null;
        for (int index = 0; index < arguments.length; index++) {
            if (arguments[index].equals(OPTION)) {
                if (directory != null || index + 1 == arguments.length) throw new IllegalArgumentException("Invalid task group argument");
                directory = Path.of(arguments[++index]);
                if (!directory.isAbsolute() || !Files.isDirectory(directory)) throw new IllegalArgumentException("Task group directory must exist and be absolute");
            } else forwarded.add(arguments[index]);
        }
        return new Invocation(forwarded.toArray(String[]::new), directory);
    }

    public static Task[] capture(Task[] tasks, Store store) {
        Task[] captured = new Task[tasks.length];
        for (int index = 0; index < tasks.length; index++) {
            Task task = tasks[index];
            TaskCompleteness.SuiteName name = new TaskCompleteness.SuiteName(task.taskDef().fullyQualifiedName());
            captured[index] = new GroupTask(task, store, name, name);
        }
        return captured;
    }

    private static final class GroupTask implements Task {
        private final Task delegate;
        private final Store store;
        private final TaskCompleteness.SuiteName listener;
        private GroupTask(Task delegate, Store store, TaskCompleteness.SuiteName listener, TaskCompleteness.SuiteName result) {
            this.delegate = Objects.requireNonNull(delegate);
            this.store = Objects.requireNonNull(store);
            this.listener = listener;
            store.publish(new Mapping(listener, result));
        }
        @Override public TaskDef taskDef() { return delegate.taskDef(); }
        @Override public String[] tags() { return delegate.tags(); }
        @Override public Task[] execute(EventHandler handler, Logger[] loggers) {
            Task[] children = delegate.execute(handler, loggers);
            Task[] captured = new Task[children.length];
            for (int index = 0; index < children.length; index++) {
                TaskCompleteness.SuiteName child = new TaskCompleteness.SuiteName(listener.value() + "-" + index);
                captured[index] = new GroupTask(children[index], store, child, listener);
            }
            return captured;
        }
    }
}
