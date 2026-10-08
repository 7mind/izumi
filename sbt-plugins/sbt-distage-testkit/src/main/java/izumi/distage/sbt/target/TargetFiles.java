package izumi.distage.sbt.target;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class TargetFiles {
    private TargetFiles() {}

    @FunctionalInterface
    public interface Writer { void write(Path temporary) throws IOException; }

    @FunctionalInterface
    public interface Reader<A, E extends Exception> { A read(Path path) throws IOException, E; }

    public static void publish(Path destination, String prefix, Writer writer) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), prefix, ".tmp");
        try {
            writer.write(temporary);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }

    public static <A, E extends Exception> List<A> read(Path directory, Predicate<Path> select, Reader<A, E> reader) throws IOException, E {
        List<A> records = new ArrayList<>();
        try (var entries = Files.list(directory)) {
            for (Path path : entries.filter(select).toList()) records.add(reader.read(path));
        }
        return List.copyOf(records);
    }
}
