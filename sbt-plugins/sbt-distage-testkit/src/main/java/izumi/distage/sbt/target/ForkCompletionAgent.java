package izumi.distage.sbt.target;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import sbt.testing.Task;
import sbt.testing.TaskDef;

import static izumi.distage.testkit.protocol.ForkCompletionOwnership.DIRECTORY_PROPERTY;
import static izumi.distage.testkit.protocol.ForkCompletionOwnership.PREFIX_PROPERTY;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class ForkCompletionAgent extends Thread {
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

    public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
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
        System.setProperty(PREFIX_PROPERTY, prefix.toString());
        installExitCapture(instrumentation);
        installTaskCompletion(instrumentation, agent);
        System.setProperty(DIRECTORY_PROPERTY, prefix.getParent().toString());
        agent.publish("entered", agent.pid);
        Runtime.getRuntime().addShutdownHook(agent);
    }

    private static void installExitCapture(Instrumentation instrumentation) throws Exception {
        Class<?> shutdown = Class.forName("java.lang.Shutdown", false, null);
        shutdown.getDeclaredMethod("exit", int.class);
        shutdown.getDeclaredMethod("runHooks");
        CaptureListener listener = new CaptureListener();
        new AgentBuilder.Default().ignore(none()).disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(listener)
            .type(named("java.lang.Shutdown"))
            .transform((builder, type, loader, module, domain) -> builder
                .visit(Advice.to(ExitRequest.class).on(named("exit").and(takesArguments(int.class))))
                .visit(Advice.to(ChosenExit.class).on(named("runHooks").and(takesArguments(0)))))
            .installOn(instrumentation);
        if (!listener.installed.get() || listener.error.get() != null) {
            throw new IllegalStateException("Fork exit capture was not installed", listener.error.get());
        }
    }

    private static void installTaskCompletion(Instrumentation instrumentation, ForkCompletionAgent agent) {
        new AgentBuilder.Default().ignore(none()).disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new CompletionListener(agent))
            .type(hasSuperType(named("sbt.testing.Framework")).and(not(isInterface())))
            .transform((builder, type, loader, module, domain) -> builder
                .visit(Advice.to(FrameworkArguments.class).on(named("runner").and(takesArguments(String[].class, String[].class, ClassLoader.class)))))
            .installOn(instrumentation);
        new AgentBuilder.Default().ignore(none()).disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new CompletionListener(agent))
            .type(hasSuperType(named("sbt.testing.Runner")).and(not(isInterface())))
            .transform((builder, type, loader, module, domain) -> builder
                .visit(Advice.to(RunnerTasks.class).on(named("tasks").and(takesArguments(TaskDef[].class)).and(returns(Task[].class)))))
            .installOn(instrumentation);
    }

    @Override
    public void run() {
        try {
            publish("shutdown", pid);
            String exit = Files.readString(path("exit"), StandardCharsets.UTF_8);
            if (!exit.equals("0\ttrue")) {
                publish("failed", "Fork exited before normal worker completion: " + exit);
                return;
            }
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
        TargetFiles.publish(path(suffix), "fork-publication-", temporary -> Files.writeString(temporary, value, StandardCharsets.UTF_8));
    }

    private static final class CaptureListener extends AgentBuilder.Listener.Adapter {
        private final AtomicBoolean installed = new AtomicBoolean();
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onTransformation(TypeDescription type, ClassLoader loader, JavaModule module, boolean loaded, DynamicType dynamicType) {
            installed.set(true);
        }

        @Override
        public void onError(String name, ClassLoader loader, JavaModule module, boolean loaded, Throwable cause) {
            error.set(cause);
        }
    }

    private static final class CompletionListener extends AgentBuilder.Listener.Adapter {
        private final ForkCompletionAgent agent;

        private CompletionListener(ForkCompletionAgent agent) {
            this.agent = agent;
        }

        @Override
        public void onError(String name, ClassLoader loader, JavaModule module, boolean loaded, Throwable cause) {
            try {
                agent.publish("failed", "Target task completion instrumentation failed: " + name + ": " + cause);
            } catch (Throwable publication) {
                cause.addSuppressed(publication);
            }
            cause.printStackTrace(System.err);
            Runtime.getRuntime().halt(FAILURE_EXIT);
        }
    }

    public static final class RunnerTasks {
        @Advice.OnMethodExit
        public static void exit(@Advice.Argument(0) TaskDef[] definitions, @Advice.Return(readOnly = false) Task[] tasks) {
            if (TaskCompleteness.isOutermostRunnerTasks()) {
                Path directory = Paths.get(java.util.Objects.requireNonNull(System.getProperty(DIRECTORY_PROPERTY), "Missing target command ownership"));
                tasks = TaskCompleteness.normalise(definitions, tasks, new TaskCompleteness.FileCompletionStore(directory));
                tasks = TaskCompleteness.captureForeign(tasks, new ForeignRunReports.FileStore(directory));
            }
        }
    }

    public static final class FrameworkArguments {
        @Advice.OnMethodEnter
        public static void enter(@Advice.Argument(value = 0, readOnly = false) String[] arguments) {
            TaskGroups.Invocation invocation = TaskGroups.parse(arguments);
            if (invocation.directory() != null) {
                Path directory = Paths.get(java.util.Objects.requireNonNull(System.getProperty(DIRECTORY_PROPERTY), "Missing task group ownership"));
                if (!directory.equals(invocation.directory())) throw new IllegalArgumentException("Task group ownership differs from fork admission");
            }
            arguments = invocation.arguments();
        }
    }

    public static final class ExitRequest {
        @Advice.OnMethodEnter
        public static void enter(@Advice.Argument(0) int status) throws IOException {
            Path prefix = Paths.get(System.getProperty(PREFIX_PROPERTY));
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            boolean main = false;
            // The pinned worker calls System.exit directly from its top-level main after replying.
            for (int index = 0; index < stack.length; index++) {
                if (stack[index].getClassName().equals("java.lang.System") && stack[index].getMethodName().equals("exit")) {
                    main = index + 2 == stack.length && stack[index + 1].getMethodName().equals("main");
                }
            }
            Path temporary = Files.createTempFile(prefix.getParent(), "fork-exit-request-", ".tmp");
            try {
                Files.writeString(temporary, status + "\t" + main, StandardCharsets.UTF_8);
                Path request = prefix.resolveSibling(prefix.getFileName() + ".exit-thread-" + Thread.currentThread().getId());
                Files.move(temporary, request, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static final class ChosenExit {
        @Advice.OnMethodEnter
        public static void enter() throws IOException {
            Path prefix = Paths.get(System.getProperty(PREFIX_PROPERTY));
            // runHooks executes under the shutdown lock on the actual initiating thread.
            Path request = prefix.resolveSibling(prefix.getFileName() + ".exit-thread-" + Thread.currentThread().getId());
            String exit = Files.isRegularFile(request) ? Files.readString(request, StandardCharsets.UTF_8) : "0\tfalse";
            Path temporary = Files.createTempFile(prefix.getParent(), "fork-chosen-exit-", ".tmp");
            try {
                Files.writeString(temporary, exit, StandardCharsets.UTF_8);
                Files.move(temporary, prefix.resolveSibling(prefix.getFileName() + ".exit"), StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
