package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.*

import sbt.testing.{Event, EventHandler, Fingerprint, Logger, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, SuiteSelector, Task, TaskDef}

import java.nio.file.{Files, Paths}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

object ForkReceiptRunnerFixtures {
  private final val WaitSeconds = 10L
  private final val PollMillis = 1L
  private val suite = ForkReceiptSuite("fixture.SelectedSuite")
  private val policy = ForkReceiptWaitPolicy(TimeUnit.SECONDS.toNanos(WaitSeconds), PollMillis)
  private val fingerprint = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = "fixture.BaseSuite"
    override def requireNoArgConstructor(): Boolean = true
  }

  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1 || (arguments.length == 2 && arguments(1) == "metadata-failure"), "Expected a new owned fixture directory and optional regression mode")
    val parent = Paths.get(arguments.head).toAbsolutePath
    require(!Files.exists(parent), "Fixture directory must be new")
    val _ = Files.createDirectories(parent)
    try {
      if (arguments.length == 2) metadataFailure()
      else {
        contract("memory", () => { val store = new MemoryStore; Views(store, store) })
        contract("filesystem", () => { val store = FileForkReceiptStore.create(parent); Views(store, FileForkReceiptStore.open(store.directory)) })
      }
    } finally Files.delete(parent)
  }

  private def metadataFailure(): Unit = {
    val original = new LinkageError("task metadata failure after selection")
    val metadataFailure = new AtomicReference[Option[Throwable]](None)
    val entered = new CountDownLatch(1)
    val finished = new CountDownLatch(1)
    val outcome = new AtomicReference[Option[Throwable]](None)
    val delegate = new Runner {
      override def args(): Array[String] = Array.empty
      override def remoteArgs(): Array[String] = Array.empty
      override def done(): String = { entered.countDown(); "complete" }
      override def tasks(definitions: Array[TaskDef]): Array[Task] = definitions.map { definition => new Task {
        override def taskDef(): TaskDef = { metadataFailure.get().foreach(throw _); definition }
        override def tags(): Array[String] = Array.empty
        override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = throw new IllegalStateException("Metadata failure did not precede task execution: " + handler + loggers.length)
      }}
    }
    val store = new MemoryStore
    val runner = new ForkReceiptRunner(delegate, store, policy)
    val task = runner.tasks(Array(definition(suite))).head
    metadataFailure.set(Some(original))
    val handler = new EventHandler { override def handle(event: Event): Unit = throw new IllegalStateException("Unexpected metadata-failure event: " + event) }
    require(captured { val _ = task.execute(handler, Array.empty); () } eq original, "Task metadata exception lost its identity")
    val target = new Thread(() => {
      try { val _ = runner.done() } catch { case cause: Throwable => outcome.set(Some(cause)) }
      finally finished.countDown()
    })
    target.start()
    try {
      require(entered.await(WaitSeconds, TimeUnit.SECONDS), "Delegate completion did not begin")
      require(finished.await(1L, TimeUnit.SECONDS), "FORK_RUNNER_METADATA_ADMISSION_LEAK: done waits after failed task metadata")
      require(outcome.get().exists(cause => cause.isInstanceOf[IllegalArgumentException] && cause.getMessage.contains("every selected task")), "Incomplete selected task was not rejected: " + outcome.get())
    } finally { target.interrupt(); target.join(TimeUnit.SECONDS.toMillis(WaitSeconds)); store.close() }
    require(!target.isAlive, "Metadata-failure fixture waiter survived cleanup")
    println("FORK_RUNNER_METADATA_CHECK_OK failed task metadata leaves no active task admission")
  }

  private def contract(label: String, create: () => Views): Unit = {
    withViews(create) { views =>
      val statuses = Vector(Status.Success, Status.Failure, Status.Error, Status.Skipped, Status.Ignored, Status.Canceled, Status.Pending)
      val delegate = new ManualRunner(statuses, None, None)
      val pending = new CountDownLatch(2)
      val reader = new ForkReceiptReader {
        override def received(name: ForkReceiptSuite): Option[ForkReceiptSummary] = {
          val value = views.reader.received(name)
          if (value.isEmpty) pending.countDown()
          value
        }
      }
      val runner = new ForkReceiptRunner(delegate, reader, policy)
      require(runner.args().toVector == delegate.args().toVector && runner.remoteArgs().toVector == delegate.remoteArgs().toVector, "Wrapper changed public runner arguments")
      val count = new AtomicInteger(0)
      val handler = new EventHandler { override def handle(event: Event): Unit = { require(event.fullyQualifiedName() == suite.value); val _ = count.incrementAndGet() } }
      Vector(runner.tasks(Array(definition(suite))), runner.tasks(Array(definition(suite)))).foreach { tasks =>
        require(tasks.length == 1 && tasks.head.tags().toVector == Vector("fixture"), "Wrapper changed tasks or tags")
        require(tasks.head.execute(handler, Array.empty).isEmpty, "Wrapper changed nested tasks")
      }
      require(count.get() == statuses.size * 2, "Wrapper changed event delivery")
      val finished = new CountDownLatch(1)
      val outcome = new AtomicReference[Option[Either[Throwable, String]]](None)
      val target = new Thread(() => {
        try outcome.set(Some(Right(runner.done()))) catch { case cause: Throwable => outcome.set(Some(Left(cause))) }
        finally finished.countDown()
      })
      target.start()
      try {
        require(pending.await(WaitSeconds, TimeUnit.SECONDS) && finished.getCount == 1, "Target did not wait for host receipt")
        views.writer.publish(suite, ForkReceiptSummary(2, ForkReceiptCounts(2, 2, 2, 2, 2, 2, 2)))
        require(finished.await(WaitSeconds, TimeUnit.SECONDS) && outcome.get().contains(Right("complete")), "Target completion differs: " + outcome.get())
      } finally { target.interrupt(); target.join(TimeUnit.SECONDS.toMillis(WaitSeconds)) }
      require(!target.isAlive && delegate.completions.get() == 1 && runner.done() == "complete" && delegate.completions.get() == 1, "Repeated done changed lifecycle completion")
      rejected { val _ = runner.tasks(Array(definition(suite))); () }
    }
    withViews(create) { views =>
      val original = new LinkageError("task failure after buffered successes")
      val runner = new ForkReceiptRunner(new ManualRunner(Vector(Status.Success, Status.Success), Some(original), None), views.reader, policy)
      val delivered = new AtomicInteger(0)
      val handler = new EventHandler { override def handle(event: Event): Unit = { require(event.fullyQualifiedName() == suite.value); val _ = delivered.incrementAndGet() } }
      val cause = captured { val _ = runner.tasks(Array(definition(suite))).head.execute(handler, Array.empty); () }
      require((cause eq original) && delivered.get() == 2, "Wrapper lost the original task exception or callback delivery")
      views.writer.publish(suite, ForkReceiptSummary(1, ForkReceiptCounts(0, 0, 1, 0, 0, 0, 0)))
      require(runner.done() == "complete", "Wrapper did not count the SDK's one replacement error")
    }
    withViews(create) { views =>
      val runner = new ForkReceiptRunner(new ManualRunner(Vector(Status.Success), None, None), views.reader, policy)
      val tasks = runner.tasks(Array(definition(suite), definition(ForkReceiptSuite("fixture.OmittedSuite"))))
      val handler = new EventHandler { override def handle(event: Event): Unit = { val _ = event } }
      val _ = tasks.head.execute(handler, Array.empty)
      views.writer.publish(suite, ForkReceiptSummary(1, ForkReceiptCounts(1, 0, 0, 0, 0, 0, 0)))
      val cause = captured { val _ = runner.done(); () }
      require(cause.isInstanceOf[IllegalArgumentException] && cause.getMessage.contains("every selected task"), "Omitted task did not fail before acknowledgement")
      require(captured { val _ = runner.done(); () } eq cause, "Repeated done lost the original completion failure")
    }
    withViews(create) { views =>
      val original = new LinkageError("delegate completion failure")
      val runner = new ForkReceiptRunner(new ManualRunner(Vector.empty, None, Some(original)), views.reader, policy)
      require((captured { val _ = runner.done(); () } eq original) && (captured { val _ = runner.done(); () } eq original), "Repeated done lost a fatal completion exception")
    }
    println("FORK_RUNNER_CHECK_OK " + label + " repeated groups, held receipt, SDK replacement, skipped task and repeated done")
  }

  private def definition(name: ForkReceiptSuite): TaskDef = new TaskDef(name.value, fingerprint, false, Array(new SuiteSelector))
  private final case class Views(writer: ForkReceiptStore, reader: ForkReceiptReader)
  private def withViews(create: () => Views)(body: Views => Unit): Unit = { val views = create(); try body(views) finally views.writer.close() }
  private def captured(body: => Unit): Throwable = {
    var result = Option.empty[Throwable]
    try body catch { case cause: Throwable => result = Some(cause) }
    result.getOrElse(throw new IllegalStateException("Expected a fixture failure"))
  }
  private def rejected(body: => Unit): Unit = require(captured(body).isInstanceOf[IllegalArgumentException], "Spent runner did not reject")

  private final class ManualRunner(statuses: Vector[Status], taskFailure: Option[Throwable], completionFailure: Option[Throwable]) extends Runner {
    val completions = new AtomicInteger(0)
    override def args(): Array[String] = Array("unchanged")
    override def remoteArgs(): Array[String] = Array("remote")
    override def done(): String = { val _ = completions.incrementAndGet(); completionFailure.foreach(throw _); "complete" }
    override def tasks(definitions: Array[TaskDef]): Array[Task] = definitions.map { definition => new Task {
      override def taskDef(): TaskDef = definition
      override def tags(): Array[String] = Array("fixture")
      override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
        val _ = loggers
        statuses.foreach { eventStatus => handler.handle(new Event {
          override def fullyQualifiedName(): String = definition.fullyQualifiedName()
          override def fingerprint(): Fingerprint = definition.fingerprint()
          override def selector(): Selector = new SuiteSelector
          override def status(): Status = eventStatus
          override def throwable(): OptionalThrowable = new OptionalThrowable
          override def duration(): Long = 0L
        }) }
        taskFailure.foreach(throw _)
        Array.empty
      }
    }}
  }

  private final class MemoryStore extends ForkReceiptStore {
    private var receipts = Map.empty[ForkReceiptSuite, ForkReceiptSummary]
    private var closed = false
    override def publish(name: ForkReceiptSuite, summary: ForkReceiptSummary): Unit = synchronized { require(!closed); receipts = receipts.updated(name, summary) }
    override def received(name: ForkReceiptSuite): Option[ForkReceiptSummary] = synchronized { require(!closed); receipts.get(name) }
    override def close(): Unit = synchronized { closed = true; receipts = Map.empty }
  }
}
