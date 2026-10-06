package izumi.distage.sbt.target;

import sbt.testing.AnnotatedFingerprint;
import sbt.testing.Event;
import sbt.testing.Fingerprint;
import sbt.testing.OptionalThrowable;
import sbt.testing.Selector;
import sbt.testing.Status;
import sbt.testing.SubclassFingerprint;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ForeignRunReports {
    private static final String FILE_PREFIX = "foreign-";
    private static final String FILE_SUFFIX = ".foreign-report";

    private ForeignRunReports() {}

    public record Report(UUID token, TaskCompleteness.SuiteName owner, TaskCompleteness.SuiteName group, long pid, List<EventSnapshot> events) implements Serializable {
        public Report {
            Objects.requireNonNull(token, "Missing foreign report token");
            Objects.requireNonNull(owner, "Missing foreign report owner");
            Objects.requireNonNull(group, "Missing foreign report group");
            if (pid <= 0) throw new IllegalArgumentException("Invalid foreign report process");
            events = List.copyOf(events);
        }
    }

    public record EventSnapshot(String fullyQualifiedName, Fingerprint fingerprint, Selector selector, Status status, OptionalThrowable throwable, long duration) implements Event, Serializable {
        public static EventSnapshot from(Event event) {
            Fingerprint fingerprint = event.fingerprint();
            Fingerprint copy;
            if (fingerprint instanceof SubclassFingerprint subclass) copy = new SubclassSnapshot(subclass.isModule(), subclass.superclassName(), subclass.requireNoArgConstructor());
            else if (fingerprint instanceof AnnotatedFingerprint annotated) copy = new AnnotationSnapshot(annotated.isModule(), annotated.annotationName());
            else throw new IllegalArgumentException("Unsupported foreign event fingerprint: " + fingerprint);
            Selector selector = event.selector();
            if (!(selector instanceof Serializable)) throw new IllegalArgumentException("Foreign event selector must be serializable: " + selector);
            OptionalThrowable cause = event.throwable();
            return new EventSnapshot(event.fullyQualifiedName(), copy, selector, event.status(), cause.isDefined() ? new OptionalThrowable(new RemoteFailure(cause.get())) : new OptionalThrowable(), event.duration());
        }
    }

    public record SubclassSnapshot(boolean isModule, String superclassName, boolean requireNoArgConstructor) implements SubclassFingerprint, Serializable {}
    public record AnnotationSnapshot(boolean isModule, String annotationName) implements AnnotatedFingerprint, Serializable {}

    public static final class RemoteFailure extends Exception {
        private RemoteFailure(Throwable cause) {
            super(cause.getClass().getName() + ": " + cause.getMessage(), cause.getCause() == null ? null : new RemoteFailure(cause.getCause()));
            setStackTrace(cause.getStackTrace());
        }
    }

    public interface Store {
        void publish(Report report);
        List<Report> completed();
    }

    public static final class FileStore implements Store {
        private final Path directory;

        public FileStore(Path directory) {
            this.directory = Objects.requireNonNull(directory, "Missing foreign report directory");
            if (!directory.isAbsolute() || !Files.isDirectory(directory)) throw new IllegalArgumentException("Foreign report directory must be an existing absolute path");
        }

        @Override public void publish(Report report) {
            Path destination = directory.resolve(FILE_PREFIX + report.token() + FILE_SUFFIX);
            if (Files.exists(destination)) throw new IllegalStateException("Duplicate foreign report token");
            try {
                Path temporary = Files.createTempFile(directory, "foreign-publication-", ".tmp");
                try {
                    try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(temporary))) { output.writeObject(report); }
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary); }
            } catch (IOException cause) { throw new IllegalStateException("Cannot publish foreign target report", cause); }
        }

        @Override public List<Report> completed() {
            List<Report> result = new ArrayList<>();
            try (var entries = Files.list(directory)) {
                for (Path path : entries.filter(value -> value.getFileName().toString().endsWith(FILE_SUFFIX)).toList()) {
                    try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(path))) {
                        Report report = (Report) input.readObject();
                        if (!path.getFileName().toString().equals(FILE_PREFIX + report.token() + FILE_SUFFIX)) throw new IllegalArgumentException("Foreign report filename differs from its token");
                        result.add(report);
                    }
                }
            } catch (IOException | ClassNotFoundException cause) { throw new IllegalStateException("Cannot read foreign target reports", cause); }
            return List.copyOf(result);
        }
    }
}
