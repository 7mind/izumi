package izumi.distage.testkit.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

public final class ForkCompletionAgent extends Thread {
    public static final String DIRECTORY_PROPERTY = "izumi.distage.fork-completion-directory";
    private static final long POLL_MILLIS = 5L;
    private static final int FAILURE_EXIT = 1;
    private final Path prefix;
    private final ProcessHandle owner;
    private final String pid;

    private ForkCompletionAgent(Path prefix, ProcessHandle owner) {
        super("distage-fork-completion");
        this.prefix = prefix;
        this.owner = owner;
        this.pid = Long.toString(ProcessHandle.current().pid());
    }

    public static void premain(String arguments) throws IOException {
        String[] fields = arguments.split(":", -1);
        if (fields.length != 2) throw new IllegalArgumentException("Invalid fork completion agent arguments");
        Path prefix = Paths.get(new String(Base64.getUrlDecoder().decode(fields[0]), StandardCharsets.UTF_8));
        if (!prefix.isAbsolute() || !Files.isDirectory(prefix.getParent())) {
            throw new IllegalArgumentException("Fork completion directory is not an existing absolute path");
        }
        ProcessHandle owner = ProcessHandle.of(Long.parseLong(fields[1])).orElseThrow(
            () -> new IllegalStateException("Fork completion owner has exited")
        );
        if (System.getProperty(DIRECTORY_PROPERTY) != null) {
            throw new IllegalStateException("Fork completion agent was installed twice");
        }
        ForkCompletionAgent agent = new ForkCompletionAgent(prefix, owner);
        agent.publish("entered", agent.pid);
        System.setProperty(DIRECTORY_PROPERTY, prefix.getParent().toString());
        Runtime.getRuntime().addShutdownHook(agent);
    }

    @Override
    public void run() {
        try {
            publish("shutdown", pid);
            Path decision = path("decision");
            while (!Files.isRegularFile(decision)) {
                if (!owner.isAlive()) throw new IllegalStateException("Fork completion owner exited before acknowledgement");
                Thread.sleep(POLL_MILLIS);
            }
            String value = Files.readString(decision, StandardCharsets.UTF_8);
            if (value.equals("commit")) publish("ready", pid);
            else if (value.equals("abort")) publish("aborted", pid);
            else throw new IllegalStateException("Invalid fork completion decision: " + value);
        } catch (Throwable cause) {
            try {
                publish("failed", cause.toString());
            } catch (Throwable publication) {
                cause.addSuppressed(publication);
            }
            cause.printStackTrace(System.err);
            Runtime.getRuntime().halt(FAILURE_EXIT);
        }
    }

    private Path path(String suffix) {
        return prefix.resolveSibling(prefix.getFileName().toString() + "." + suffix);
    }

    private void publish(String suffix, String value) throws IOException {
        Path temporary = Files.createTempFile(prefix.getParent(), "fork-publication-", ".tmp");
        try {
            Files.writeString(temporary, value, StandardCharsets.UTF_8);
            Files.move(temporary, path(suffix), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
