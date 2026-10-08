package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.{ForkReceiptAwaiter, ForkReceiptCounts, ForkReceiptReader, ForkReceiptSuite, ForkReceiptSummary, ForkReceiptWaitPolicy}

import sbt.testing.{Event, EventHandler, Logger, Runner, Status, Task, TaskDef}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration

private[bootstrap] final class ForkReceiptRunner(delegate: Runner, reader: ForkReceiptReader, policy: ForkReceiptWaitPolicy) extends Runner {
  private var registered = Map.empty[ForkReceiptSuite, Int]
  private var completed = Map.empty[ForkReceiptSuite, ForkReceiptSummary]
  private var activeTasks = 0
  private var finishing = false
  private val completion = Promise[Either[Throwable, String]]()

  override def args(): Array[String] = delegate.args()
  override def remoteArgs(): Array[String] = delegate.remoteArgs()

  override def tasks(definitions: Array[TaskDef]): Array[Task] = synchronized {
    require(!finishing, "Fork receipt runner is spent")
    val tasks = delegate.tasks(definitions)
    require(tasks.map(_.taskDef().fullyQualifiedName()).toVector == definitions.map(_.fullyQualifiedName()).toVector, "Bootstrap tasks differ from selected definitions")
    definitions.foreach { definition =>
      val suite = ForkReceiptSuite(definition.fullyQualifiedName())
      registered = registered.updated(suite, Math.addExact(registered.getOrElse(suite, 0), 1))
    }
    tasks.map { task => new Task {
      private var executed = false
      override def taskDef(): TaskDef = task.taskDef()
      override def tags(): Array[String] = task.tags()
      override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
        val suite = ForkReceiptSuite(task.taskDef().fullyQualifiedName())
        val counter = new TaskCounts(suite, handler)
        beginTask()
        try {
          synchronized { require(!executed, "Suite task has already executed"); executed = true }
          task.execute(counter, loggers)
        } catch {
          case cause: Throwable =>
            counter.replaceWithSdkError()
            throw cause
        } finally {
          try record(suite, counter.close()) finally endTask()
        }
      }
    }}
  }

  override def done(): String = {
    val first = synchronized {
      if (finishing) false else { finishing = true; true }
    }
    if (first) {
      try {
        val result = delegate.done()
        val expected = synchronized {
          while (activeTasks > 0) wait()
          require(registered == completed.map { case (suite, summary) => suite -> summary.groups }, "Fork target did not complete every selected task")
          completed
        }
        new ForkReceiptAwaiter(reader, policy).await(expected)
        val _ = completion.success(Right(result))
        result
      } catch {
        case cause: Throwable =>
          val _ = completion.success(Left(cause))
          throw cause
      }
    } else Await.result(completion.future, Duration.Inf).fold(cause => throw cause, result => result)
  }

  private def beginTask(): Unit = synchronized {
    require(!finishing, "Fork receipt runner is spent")
    activeTasks += 1
  }

  private def record(suite: ForkReceiptSuite, counts: ForkReceiptCounts): Unit = synchronized {
    val summary = completed.get(suite) match {
      case Some(previous) => ForkReceiptSummary(Math.addExact(previous.groups, 1), previous.counts + counts)
      case None => ForkReceiptSummary(1, counts)
    }
    completed = completed.updated(suite, summary)
  }

  private def endTask(): Unit = synchronized {
    activeTasks -= 1
    require(activeTasks >= 0, "Active fork task count became negative")
    notifyAll()
  }

  private final class TaskCounts(suite: ForkReceiptSuite, handler: EventHandler) extends EventHandler {
    private var counts = ForkReceiptCounts(0, 0, 0, 0, 0, 0, 0)
    private var closed = false

    override def handle(event: Event): Unit = synchronized {
      require(!closed, "Event emitted after fork task completion")
      require(event.fullyQualifiedName() == suite.value, "Fork event differs from its task suite")
      handler.handle(event)
      val increment = event.status() match {
        case Status.Success => ForkReceiptCounts(1, 0, 0, 0, 0, 0, 0)
        case Status.Failure => ForkReceiptCounts(0, 1, 0, 0, 0, 0, 0)
        case Status.Error => ForkReceiptCounts(0, 0, 1, 0, 0, 0, 0)
        case Status.Skipped => ForkReceiptCounts(0, 0, 0, 1, 0, 0, 0)
        case Status.Ignored => ForkReceiptCounts(0, 0, 0, 0, 1, 0, 0)
        case Status.Canceled => ForkReceiptCounts(0, 0, 0, 0, 0, 1, 0)
        case Status.Pending => ForkReceiptCounts(0, 0, 0, 0, 0, 0, 1)
      }
      counts = counts + increment
    }

    def replaceWithSdkError(): Unit = synchronized {
      require(!closed, "Cannot replace a completed fork task receipt")
      counts = ForkReceiptCounts(0, 0, 1, 0, 0, 0, 0)
    }

    def close(): ForkReceiptCounts = synchronized { closed = true; counts }
  }
}
