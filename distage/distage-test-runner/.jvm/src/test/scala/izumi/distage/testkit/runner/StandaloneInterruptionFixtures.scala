package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.{ExecutionContext, Future}

object StandaloneInterruptionFixtures {
  private final val TimeoutSeconds = 15L
  private final val PollMillis = 5L

  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Fixture requires its capture directory")
    val directory = Paths.get(arguments(0)).toAbsolutePath
    val identity = CatalogueIdentity(BuildId("standalone-interruption"), BuildTargetId(directory.toString), CatalogueId("standalone-interruption"))
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val input = directory.resolve("commands.jsonl")
    val output = directory.resolve("output.jsonl")
    val command = ProtocolMessage.Request(RequestOperation.Execute, RunId("standalone-interruption"), request)
    val _ = Files.write(input, (ProtocolCodec.encode(command) + "\n").getBytes(StandardCharsets.UTF_8))
    val restored = new AtomicBoolean(false)
    val failure = new AtomicReference[Throwable]()
    val caller = new Thread(() => {
      try StandaloneLauncher.main(Array(identity.build.value, identity.target.value, identity.catalogue.value, input.toString, output.toString, classOf[StandaloneInterruptedSuite].getName))
      catch { case cause: Throwable => failure.set(cause); restored.set(Thread.currentThread().isInterrupted) }
    }, "standalone-interruption-fixture-caller")
    caller.setDaemon(true)
    caller.start()
    try {
      awaitFile(directory.resolve("started"))
      caller.interrupt()
      awaitFile(directory.resolve("cancelled"))
      require(caller.isAlive && !Files.exists(directory.resolve("released")), "Caller must await the held finalizer after cancellation")
      caller.interrupt()
      val _ = Files.createFile(directory.resolve("release"))
      caller.join(TimeUnit.SECONDS.toMillis(TimeoutSeconds))
      require(!caller.isAlive && restored.get() && Files.exists(directory.resolve("released")), "Caller must terminate after release with its interrupt flag restored")
      val original = Option(failure.get()).getOrElse(throw new IllegalStateException("Interrupted standalone caller returned without its failure"))
      val causes = Vector(original) ++ Option(original.getCause).toVector ++ original.getSuppressed.toVector
      require(causes.exists(_.isInstanceOf[InterruptedException]), "Caller must retain the interruption failure")
      val source = FileProtocolFrameSource.open(output)
      val frames = try {
        val messages = Vector.newBuilder[ProtocolMessage]
        var frame = source.readFrame()
        while (frame.nonEmpty) {
          messages += ProtocolCodec.decode(frame.get).fold(error => throw new IllegalArgumentException(error.message), value => value)
          frame = source.readFrame()
        }
        messages.result()
      } finally source.close()
      val outcome = frames.last match {
        case ProtocolMessage.Completed(value) => value
        case _ => throw new IllegalStateException("Standalone cancellation must publish its terminal outcome before returning")
      }
      require(outcome.cancelled && outcome.results.size == 1 && outcome.results.head.status == TestStatus.Cancelled && outcome.failures.isEmpty, "Standalone cancellation must preserve the selected test outcome")
      val events = frames.collect { case value: ProtocolMessage.Event => value }
      require(events.map(_.sequence) == events.indices.map(_.toLong).toVector && events.last.event == RunEvent.Finished(outcome.run, outcome), "Standalone terminal events must remain contiguous")
      println("STANDALONE_INTERRUPTION_FIXTURES_OK cancellation=once finalizer=awaited restoredFlag=true callerTerminated=true terminal=last")
    } finally {
      if (!Files.exists(directory.resolve("release"))) { val _ = Files.createFile(directory.resolve("release")) }
      if (caller.isAlive) caller.interrupt()
      caller.join(TimeUnit.SECONDS.toMillis(TimeoutSeconds))
      require(!caller.isAlive, "Standalone fixture caller did not terminate during cleanup")
    }
  }

  private def awaitFile(path: Path): Unit = {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TimeoutSeconds)
    while (!Files.exists(path) && System.nanoTime() < deadline) Thread.sleep(PollMillis)
    require(Files.exists(path), s"Timed out awaiting fixture signal: $path")
  }
}

final class StandaloneInterruptedSuite extends TestSuite {
  private final val TimeoutSeconds = 30L
  private final val PollMillis = 5L

  override def register(context: RegistrationContext): RegisteredSuite = {
    val directory = Paths.get(context.target.value)
    val suite = SuiteDescriptor(SuiteId(getClass.getName), "StandaloneInterruptedSuite")
    val test = TestDescriptor(TestId(context.target, suite.id, Vector("held finalizer"), None), "held finalizer", SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
    val provider = FixtureSupport.provider { (_, run) =>
      val registration = run.cancellation.onRequest(() => {
        val _ = Files.createFile(directory.resolve("cancelled"))
        Future.unit
      })
      run.emit(ProviderEvent.TestStarted(test.id))
      Future {
        val _ = Files.createFile(directory.resolve("started"))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TimeoutSeconds)
        while (!Files.exists(directory.resolve("release")) && System.nanoTime() < deadline) Thread.sleep(PollMillis)
        require(Files.exists(directory.resolve("release")), "Fixture finalizer was not released")
        val _ = Files.createFile(directory.resolve("released"))
        val result = TestResult(test.id, if (run.cancellation.isRequested) TestStatus.Cancelled else TestStatus.Succeeded, None, 1L)
        run.emit(ProviderEvent.TestCompleted(result))
        ProviderOutcome(Vector(result), Vector.empty, run.cancellation.isRequested)
      }(ExecutionContext.global).flatMap(value => registration.close().map(_ => value)(ExecutionContext.global))(ExecutionContext.global)
    }
    RegisteredSuite(suite, Vector(test), provider)
  }
}
