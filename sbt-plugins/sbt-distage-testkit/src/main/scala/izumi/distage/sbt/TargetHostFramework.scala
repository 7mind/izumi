package izumi.distage.sbt

import izumi.distage.testkit.protocol.*

import sbt.testing.{Event, EventHandler, Fingerprint, Framework, Logger, NestedTestSelector, OptionalThrowable, Runner, Selector, Status, SuiteSelector, Task, TaskDef, TestSelector}

import java.net.{InetAddress, ServerSocket, Socket}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.util.control.NonFatal

/** Projects a platform SDK aggregate onto ordinary, independently scheduled SBT suite tasks. */
private[sbt] final class TargetHostFramework(val platform: Framework) extends Framework {
  override def name(): String = platform.name()
  override def fingerprints(): Array[Fingerprint] = platform.fingerprints()

  override def runner(arguments: Array[String], remoteArguments: Array[String], loader: ClassLoader): Runner = {
    val invocation = ForkReceiptArguments.parse(arguments.toVector, remoteArguments.toVector)
    val remote = remoteArguments.toVector
    val fork = remote.indexOf(ForkReceiptArguments.ForkDirectoryOption)
    val forwardedRemote = if (fork < 0) remote else remote.patch(fork, Nil, 2)
    new Runner {
      private var spent = false
      private val groups = mutable.ArrayBuffer.empty[(Runner, TargetHostControl)]
      override def args(): Array[String] = arguments.clone()
      override def remoteArgs(): Array[String] = remoteArguments.clone()
      override def tasks(definitions: Array[TaskDef]): Array[Task] = synchronized {
        require(!spent, "Target host runner is spent")
        require(definitions.map(_.fullyQualifiedName()).distinct.length == definitions.length, "Duplicate target host suite identities")
        if (definitions.isEmpty) Array.empty
        else {
          val control = new TargetHostControl
          val aggregate = try {
            val original = platform.runner((invocation.arguments ++ Vector("--distage-control-port", control.port.toString)).toArray, forwardedRemote.toArray, loader)
            groups += original -> control
            val selected = definitions.toVector.sortBy(_.fullyQualifiedName())
            val aggregates = original.tasks(selected.toArray)
            require(aggregates.length == 1, "Target SDK must return one aggregate task for a selected suite group")
            Right(aggregates.head)
          } catch {
            case NonFatal(cause) => Left(cause)
          }
          val selected = definitions.toVector.sortBy(_.fullyQualifiedName())
          val group = new TargetHostGroup(selected, aggregate, control, invocation.eventDirectory)
          selected.map(definition => new TargetHostSuiteTask(definition, group): Task).toArray
        }
      }
      override def done(): String = synchronized {
        require(!spent, "Target host runner is spent")
        spent = true
        try groups.map { case (original, _) => original.done() }.filter(_.nonEmpty).mkString("\n")
        finally groups.foreach { case (_, control) => control.close() }
      }
    }
  }
}

private[sbt] final case class TargetHostOutcome(events: Map[String, Vector[Event]], failures: Vector[Throwable])

private[sbt] final class TargetHostGroup(definitions: Vector[TaskDef], aggregate: Either[Throwable, Task], control: TargetHostControl, directory: Option[Path]) {
  private var launched = false
  private var outcome = Option.empty[TargetHostOutcome]

  def tags(): Array[String] = aggregate.fold(_ => Array.empty[String], _.tags())

  def execute(loggers: Array[Logger]): TargetHostOutcome = {
    val first = synchronized {
      val start = !launched
      launched = true
      start
    }
    if (first) {
      val worker = new Thread(() => {
        var result = TargetHostOutcome(Map.empty, Vector(new IllegalStateException("Target aggregate ended without an outcome")))
        try {
          aggregate match {
            case Left(cause) => result = TargetHostOutcome(Map.empty, Vector(cause))
            case Right(task) =>
              val buffer = new TargetHostEvents(definitions, control, directory)
              val failure = try {
                require(task.execute(buffer, loggers).isEmpty, "Target aggregate returned nested tasks")
                None
              } catch {
                case cause: InterruptedException => Some(cause)
                case NonFatal(cause) => Some(cause)
              }
              result = buffer.finish(failure)
          }
        } catch {
          case NonFatal(cause) => result = TargetHostOutcome(Map.empty, Vector(cause))
        } finally {
          try control.close()
          catch { case NonFatal(cause) => result = result.copy(failures = result.failures :+ cause) }
          synchronized { outcome = Some(result); notifyAll() }
        }
      }, "distage-target-sdk-" + UUID.randomUUID().toString)
      worker.start()
      while (worker.isAlive) {
        try worker.join()
        catch { case _: InterruptedException => control.cancel() }
      }
    }
    synchronized {
      while (outcome.isEmpty) {
        try wait()
        catch { case _: InterruptedException => control.cancel() }
      }
      outcome.get
    }
  }
}

private[sbt] final class TargetHostEvents(definitions: Vector[TaskDef], control: TargetHostControl, directory: Option[Path]) extends EventHandler {
  private val selected = definitions.map(value => value.fullyQualifiedName() -> value).toMap
  private val events = mutable.LinkedHashMap.empty[String, Vector[Event]]
  private val results = mutable.ArrayBuffer.empty[TestResult]
  private val owners = mutable.Map.empty[SuiteId, String]
  private val started = mutable.Set.empty[TestId]
  private var protocolFailure = Option.empty[Throwable]
  private var sequence = 0L
  private var run = Option.empty[RunId]
  private var finished = Option.empty[RunOutcome]
  private var terminal = Option.empty[RunOutcome]
  private var closed = false
  private val channel = directory.map(_.resolve(UUID.randomUUID().toString + ".jsonl"))

  override def handle(event: Event): Unit = synchronized {
    if (protocolFailure.isEmpty) {
      try accept(event)
      catch {
        case NonFatal(cause) =>
          protocolFailure = Some(cause)
          control.cancel()
      }
    }
  }

  private def accept(event: Event): Unit = {
    require(!closed && terminal.isEmpty, "Target event arrived after application completion")
    event.selector() match {
      case nested: NestedTestSelector if nested.suiteId() == "$distage-protocol-v4" =>
        val frame = nested.testName()
        require(!frame.contains('\n') && !frame.contains('\r'), "Target channel framing violation")
        val message = ProtocolCodec.decode(frame).fold(error => throw new IllegalArgumentException(error.message), identity)
        channel.foreach(path => { val _ = Files.writeString(path, frame + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND) })
        message match {
          case ProtocolMessage.Event(index, value) =>
            require(index == sequence, "Target event sequence is not contiguous")
            sequence += 1
            value match {
              case RunEvent.Started(id) =>
                require(run.isEmpty, "Duplicate target application start")
                run = Some(id)
                control.started(id)
              case other =>
                require(run.contains(other.run) && finished.isEmpty, "Target event belongs to an inactive application")
                other match {
                  case RunEvent.TestStarted(_, test) =>
                    require(selected.contains(event.fullyQualifiedName()), "Target test owner is not selected")
                    require(owners.get(test.suite).forall(_ == event.fullyQualifiedName()), "Target logical suite changed its class owner")
                    owners.update(test.suite, event.fullyQualifiedName())
                    require(started.add(test), "Duplicate target test start")
                  case RunEvent.TestCompleted(_, result) =>
                    require(started.contains(result.id) && owners.get(result.id.suite).contains(event.fullyQualifiedName()), "Target completion has no matching test owner")
                    val definition = selected.getOrElse(event.fullyQualifiedName(), throw new IllegalArgumentException("Target result belongs to an unselected suite"))
                    require(!results.exists(_.id == result.id), "Duplicate target test completion")
                    results += result
                    val projected = new TargetHostTestEvent(definition, result)
                    val name = definition.fullyQualifiedName()
                    events.update(name, events.getOrElse(name, Vector.empty) :+ projected)
                  case RunEvent.Finished(_, value) => finished = Some(value)
                  case _ => ()
                }
            }
          case ProtocolMessage.Completed(value) =>
            require(finished.contains(value) && value.results.map(result => result.id -> result).toMap == results.map(result => result.id -> result).toMap && value.results.size == results.size, "Target terminal differs from its event stream")
            terminal = Some(value)
          case ProtocolMessage.Rejected(_, cause) => throw ProjectedFailure.root(cause)
          case _ => throw new IllegalArgumentException("Unexpected target channel response")
        }
      case _ =>
        if (event.throwable().isDefined) throw event.throwable().get()
        else throw new IllegalArgumentException("Unattributable target SDK event")
    }
  }

  def finish(failure: Option[Throwable]): TargetHostOutcome = synchronized {
    closed = true
    val effectiveFailure = protocolFailure.orElse(failure)
    val incomplete = if (terminal.isEmpty && effectiveFailure.isEmpty) Vector(new IllegalStateException("Target aggregate returned without application completion")) else Vector.empty
    val runFailures = terminal.toVector.flatMap(_.failures.map(ProjectedFailure.root))
    val cancelled = terminal.filter(_.cancelled).toVector.map(_ => new InterruptedException("Target application was cancelled"))
    TargetHostOutcome(events.toMap, effectiveFailure.toVector ++ incomplete ++ runFailures ++ cancelled)
  }
}

private[sbt] final class TargetHostSuiteTask(definition: TaskDef, group: TargetHostGroup) extends Task {
  override def taskDef(): TaskDef = definition
  override def tags(): Array[String] = group.tags()
  override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    val outcome = group.execute(loggers)
    outcome.events.getOrElse(definition.fullyQualifiedName(), Vector.empty).foreach(handler.handle)
    outcome.failures.foreach(cause => handler.handle(new TargetHostErrorEvent(definition, cause)))
    Array.empty
  }
}

private[sbt] final class TargetHostTestEvent(definition: TaskDef, result: TestResult) extends Event {
  override def fullyQualifiedName(): String = definition.fullyQualifiedName()
  override def fingerprint(): Fingerprint = definition.fingerprint()
  override def selector(): Selector = new TestSelector(result.id.path.mkString(" "))
  override def status(): Status = result.status match {
    case TestStatus.Succeeded => Status.Success
    case TestStatus.Failed => Status.Failure
    case TestStatus.Cancelled => Status.Canceled
    case TestStatus.Skipped => Status.Skipped
  }
  override def throwable(): OptionalThrowable = result.failure.fold(new OptionalThrowable)(cause => new OptionalThrowable(ProjectedFailure.root(cause)))
  override def duration(): Long = TimeUnit.NANOSECONDS.toMillis(result.durationNanos)
}

private[sbt] final class TargetHostErrorEvent(definition: TaskDef, cause: Throwable) extends Event {
  override def fullyQualifiedName(): String = definition.fullyQualifiedName()
  override def fingerprint(): Fingerprint = definition.fingerprint()
  override def selector(): Selector = new SuiteSelector
  override def status(): Status = Status.Error
  override def throwable(): OptionalThrowable = new OptionalThrowable(cause)
  override def duration(): Long = 0L
}

private[sbt] final class TargetHostControl {
  private val listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
  val port: Int = listener.getLocalPort
  private var connection = Option.empty[Socket]
  private var run = Option.empty[RunId]
  private var cancelRequested = false
  private var cancelSent = false
  private var closed = false
  private var failure = Option.empty[Throwable]
  private val receiver = new Thread(() => {
    try {
      val accepted = listener.accept()
      synchronized {
        if (closed) accepted.close()
        else { connection = Some(accepted); sendCancellation() }
      }
    } catch {
      case NonFatal(cause) => synchronized { if (!closed) failure = Some(cause) }
    }
  }, "distage-target-control-" + port)
  receiver.start()

  def started(value: RunId): Unit = synchronized {
    require(run.isEmpty, "Duplicate target control start")
    run = Some(value)
    sendCancellation()
  }
  def cancel(): Unit = synchronized {
    cancelRequested = true
    try sendCancellation()
    catch { case NonFatal(cause) => failure = Some(cause) }
  }
  private def sendCancellation(): Unit = {
    failure.foreach(throw _)
    if (cancelRequested && !cancelSent) {
      for (socket <- connection; selected <- run) {
        val bytes = (ProtocolCodec.encode(ProtocolMessage.Cancel(selected)) + "\n").getBytes(StandardCharsets.UTF_8)
        socket.getOutputStream.write(bytes)
        socket.getOutputStream.flush()
        cancelSent = true
      }
    }
  }
  def close(): Unit = {
    synchronized {
      if (!closed) {
        closed = true
        listener.close()
        connection.foreach(_.close())
      }
    }
    var joined = false
    while (!joined) {
      try { receiver.join(); joined = true }
      catch { case _: InterruptedException => () }
    }
    synchronized { failure.foreach(throw _) }
  }
}
