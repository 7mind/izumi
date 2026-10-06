import sbt._
import sbt.Keys._
import sbt.testing.{Event, EventHandler, Fingerprint, Framework, NestedTestSelector, OptionalThrowable, Runner, Selector, Status, SuiteSelector, TaskDef, TestSelector, Logger => TestLogger, Task => TestTask}
import scala.collection.mutable
import izumi.distage.testkit.protocol.*
import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.util.control.NonFatal
object TransportJsProjectionPlugin extends AutoPlugin {
  override def requires: Plugins = org.scalajs.sbtplugin.ScalaJSPlugin
  override def projectSettings: Seq[Def.Setting[_]] = TransportProjection.settings
}

object TransportNativeProjectionPlugin extends AutoPlugin {
  override def requires: Plugins = scala.scalanative.sbtplugin.ScalaNativePlugin
  override def projectSettings: Seq[Def.Setting[_]] = TransportProjection.settings
}

sealed trait ProjectionMode
object ProjectionMode {
  val Property = "transport.projection"
  case object PerSuite extends ProjectionMode
  case object AggregateOnly extends ProjectionMode

  def fromSystemProperty(): ProjectionMode = sys.props.get(Property) match {
    case None | Some("per-suite") => PerSuite
    case Some("aggregate") => AggregateOnly
    case Some(other) => throw new MessageOnlyException(s"-D$Property=$other is neither per-suite nor aggregate")
  }
}

object TransportProjection {
  val TargetFramework: TestFramework = TestFramework("izumi.distage.testkit.runner.bootstrap.Framework")

  /** Composes with the platform plugin's host proxy: its entry is wrapped, never recreated. */
  def settings: Seq[Def.Setting[_]] = Seq(
    Test / loadedTestFrameworks := Def.uncached {
      val platformFrameworks = (Test / loadedTestFrameworks).value
      val project = thisProject.value.id
      val trace: String => Unit = line => System.out.println(line)
      ProjectionMode.fromSystemProperty() match {
        case ProjectionMode.AggregateOnly =>
          trace(s"PROJECTION_MODE aggregate project=$project")
          platformFrameworks
        case ProjectionMode.PerSuite =>
          val platform = platformFrameworks.getOrElse(TargetFramework,
            throw new MessageOnlyException(s"$TargetFramework is not loaded by the $project platform test adapter"))
          trace(s"PROJECTION_MODE per-suite project=$project platform=$platform")
          platformFrameworks.updated(TargetFramework, new ProjectionFramework(platform, trace))
      }
    }
  )
}

final case class SharingGroup(label: String, members: Seq[TaskDef]) {
  def names: Seq[String] = members.map(_.fullyQualifiedName())
}

/** Decides which selected suites share one target application run. */
trait SharingPolicy {
  def groups(selected: Seq[TaskDef]): Seq[SharingGroup]
}

object SharingPolicy {
  val TwoGroupsArgument = "two-groups"
  val SecondGroupStart = "candidate.SuiteD"

  def fromArguments(arguments: Array[String]): SharingPolicy =
    if (arguments.contains(TwoGroupsArgument)) TwoGroups else AllSelected

  object AllSelected extends SharingPolicy {
    def groups(selected: Seq[TaskDef]): Seq[SharingGroup] =
      if (selected.isEmpty) Nil else Seq(SharingGroup("all", selected.sortBy(_.fullyQualifiedName())))
  }

  object TwoGroups extends SharingPolicy {
    def groups(selected: Seq[TaskDef]): Seq[SharingGroup] =
      selected.groupBy(d => if (d.fullyQualifiedName() < SecondGroupStart) "ABC" else "DE").toSeq.sortBy(_._1).map {
        case (label, members) => SharingGroup(label, members.sortBy(_.fullyQualifiedName()))
      }
  }
}

final class ProjectionFramework(platform: Framework, trace: String => Unit) extends Framework {
  def name(): String = platform.name()
  def fingerprints(): Array[Fingerprint] = platform.fingerprints()
  def runner(arguments: Array[String], remoteArguments: Array[String], loader: ClassLoader): Runner =
    {
      val control = new CandidateControl
      new ProjectionRunner(platform.runner(arguments ++ Array("--distage-control-port", control.port.toString), remoteArguments, loader), SharingPolicy.fromArguments(arguments), trace, control)
    }
}

/** Returns one ordinary host task per selected suite; each sharing group has one platform aggregate task. */
final class ProjectionRunner(platform: Runner, policy: SharingPolicy, trace: String => Unit, control: CandidateControl) extends Runner {
  def args(): Array[String] = platform.args()
  def remoteArgs(): Array[String] = platform.remoteArgs()
  def done(): String = platform.done()

  def tasks(selected: Array[TaskDef]): Array[TestTask] =
    policy.groups(selected.toSeq).flatMap { group =>
      val aggregates = platform.tasks(group.members.toArray)
      if (aggregates.length != 1)
        throw new IllegalStateException(s"group ${group.label} expected one platform aggregate task, got ${aggregates.length}")
      val aggregate = aggregates(0)
      trace(s"PROJECTION_GROUP group=${group.label} representative=${aggregate.taskDef().fullyQualifiedName()} suites=${group.names.mkString(",")}")
      val shared = new SharedGroup(group, aggregate, trace, control)
      group.members.map(member => new SuiteTask(member, shared, trace): TestTask)
    }.toArray
}

final case class NestedEvent(testName: String, source: Event)

final class GroupOutcome(eventsBySuite: Map[String, Seq[NestedEvent]], val failure: Option[Throwable]) {
  def eventsFor(suite: String): Seq[NestedEvent] = eventsBySuite.getOrElse(suite, Nil)
}

/** Runs the aggregate on the first executing suite task's thread and memoizes its outcome, failure included. */
final class SharedGroup(group: SharingGroup, aggregate: TestTask, trace: String => Unit, control: CandidateControl) {
  private val lock = new Object
  private var launched = false // guarded by lock
  private var outcome: Option[GroupOutcome] = None // guarded by lock

  def tags(): Array[String] = aggregate.tags()

  def outcomeFor(suite: String, loggers: Array[TestLogger]): GroupOutcome = {
    val launchHere = lock.synchronized {
      val first = !launched
      launched = true
      first
    }
    if (launchHere) launch(suite, loggers) else awaitOutcome(suite)
  }

  private def launch(suite: String, loggers: Array[TestLogger]): GroupOutcome = {
    val caller = Thread.currentThread()
    trace(s"PROJECTION_LAUNCH group=${group.label} suite=$suite thread=${caller.getName}")
    val worker = new Thread(() => {
      var result = new GroupOutcome(Map.empty, Some(new IllegalStateException(s"aggregate for group ${group.label} ended without an outcome")))
      try result = execute(loggers, caller)
      finally publish(result)
      trace(s"PROJECTION_OUTCOME group=${group.label} failure=${result.failure.map(_.toString).getOrElse("none")}")
    }, "candidate-sdk-worker-" + group.label)
    worker.start()
    var interruption = Option.empty[InterruptedException]
    while (worker.isAlive) {
      try worker.join()
      catch {
        case error: InterruptedException =>
          if (interruption.isEmpty) { interruption = Some(error); control.cancel() }
          trace(s"SDK_PARENT_INTERRUPTED worker=${worker.getName} alive=${worker.isAlive}")
      }
    }
    trace(s"SDK_WORKER_JOINED group=${group.label} interrupted=${interruption.isDefined}")
    val result = awaitOutcome(suite)
    if (interruption.isDefined) require(result.failure.isDefined, "Interrupted target did not report a failed outcome")
    result
  }

  private def execute(loggers: Array[TestLogger], caller: Thread): GroupOutcome = {
    val buffer = new SuiteEventBuffer(group.names.toSet, caller, control)
    val failure =
      try {
        val nested = aggregate.execute(buffer, loggers)
        if (nested.isEmpty) None
        else Some(new IllegalStateException(s"aggregate for group ${group.label} returned ${nested.length} nested tasks"))
      } catch {
        case error: InterruptedException => Some(error)
        case NonFatal(error) => Some(error)
      }
    try buffer.close(failure) finally control.close()
  }

  private def publish(result: GroupOutcome): Unit = lock.synchronized {
    outcome = Some(result)
    lock.notifyAll()
  }

  private def awaitOutcome(suite: String): GroupOutcome = lock.synchronized {
    if (outcome.isEmpty) trace(s"PROJECTION_WAIT group=${group.label} suite=$suite thread=${Thread.currentThread().getName}")
    while (outcome.isEmpty) lock.wait()
    outcome.get
  }
}

/** Receives aggregate events on the platform's RPC thread and attributes them by nested suite id. */
final class SuiteEventBuffer(members: Set[String], executingThread: Thread, control: CandidateControl) extends EventHandler {
  private var interruptionScheduled = false
  private val events = mutable.LinkedHashMap.empty[String, mutable.ArrayBuffer[NestedEvent]] // guarded by this
  private val violations = mutable.ArrayBuffer.empty[String] // guarded by this
  private var closed = false // guarded by this
  private var terminal = Option.empty[RunOutcome]
  private var finished = Option.empty[RunOutcome]
  private val channel = Paths.get(sys.props("candidate.frames")).resolve(UUID.randomUUID().toString + ".jsonl")
  Files.createDirectories(channel.getParent)

  def handle(event: Event): Unit = synchronized {
    if (closed) throw new IllegalStateException(s"aggregate event after its task returned: ${event.fullyQualifiedName()} ${event.selector()}")
    event.selector() match {
      case nested: NestedTestSelector if nested.suiteId() == "$distage-protocol-v4" =>
        val frame = nested.testName()
        require(frame.indexOf('\n') < 0 && frame.indexOf('\r') < 0, "Channel framing violation")
        val message = ProtocolCodec.decode(frame).fold(error => throw new IllegalArgumentException(error.message), identity)
        Files.writeString(channel, frame + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
        message match {
          case ProtocolMessage.Event(_, RunEvent.Started(run)) => control.started(run)
          case ProtocolMessage.Event(_, _: RunEvent.TestStarted) if sys.props("candidate.interrupt") == "true" && !interruptionScheduled =>
            interruptionScheduled = true
            val interrupter = new Thread(() => {
              Thread.sleep(100L)
              println("SDK_EXECUTION_INTERRUPT target=" + executingThread.getName)
              executingThread.interrupt()
            }, "candidate-sdk-interruption")
            interrupter.start()
          case ProtocolMessage.Event(_, RunEvent.TestCompleted(_, result)) =>
            require(members.contains(result.id.suite.value), "Channel result outside selected suites")
            val original = event
            val projected = new Event {
              def fullyQualifiedName(): String = original.fullyQualifiedName()
              def fingerprint(): Fingerprint = original.fingerprint()
              def selector(): Selector = new NestedTestSelector(result.id.suite.value, result.id.path.mkString(" "))
              def status(): Status = result.status match {
                case TestStatus.Succeeded => Status.Success
                case TestStatus.Failed => Status.Failure
                case TestStatus.Cancelled => Status.Canceled
                case TestStatus.Skipped => Status.Skipped
              }
              def throwable(): OptionalThrowable = result.failure match {
                case Some(value) => new OptionalThrowable(ProjectedFailure.root(value))
                case None => new OptionalThrowable
              }
              def duration(): Long = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(result.durationNanos)
            }
            events.getOrElseUpdate(result.id.suite.value, mutable.ArrayBuffer.empty[NestedEvent]) += NestedEvent(result.id.path.mkString(" "), projected)
          case ProtocolMessage.Event(_, RunEvent.Finished(_, outcome)) => finished = Some(outcome)
          case ProtocolMessage.Event(_, _) => ()
          case ProtocolMessage.Completed(outcome) =>
            require(terminal.isEmpty && finished.contains(outcome), "Unreconciled application completion")
            terminal = Some(outcome)
          case ProtocolMessage.Rejected(_, cause) => throw new IllegalStateException("Target application rejected: " + cause.message)
          case _ => throw new IllegalStateException("Unexpected target channel response")
        }
      case nested: NestedTestSelector if members.contains(nested.suiteId()) =>
        events.getOrElseUpdate(nested.suiteId(), mutable.ArrayBuffer.empty[NestedEvent]) += NestedEvent(nested.testName(), event)
      case other =>
        violations += s"${event.fullyQualifiedName()} ${other}"
    }
  }

  def close(failure: Option[Throwable]): GroupOutcome = synchronized {
    closed = true
    if (failure.isEmpty) require(terminal.isDefined, "Aggregate returned without application completion")
    val violation =
      if (violations.isEmpty) None
      else Some(new IllegalStateException(s"unattributable aggregate events: ${violations.mkString("; ")}"))
    val cancelled = terminal.filter(_.cancelled).map(_ => new InterruptedException("Target application was cancelled"))
    new GroupOutcome(events.map { case (suite, buffered) => suite -> buffered.toList }.toMap, failure.orElse(violation).orElse(cancelled))
  }
}

/** An ordinary suite task: emits only its own suite's events, inside its own `execute`. */
final class SuiteTask(definition: TaskDef, group: SharedGroup, trace: String => Unit) extends TestTask {
  def taskDef(): TaskDef = definition
  def tags(): Array[String] = group.tags()

  def execute(handler: EventHandler, loggers: Array[TestLogger]): Array[TestTask] = {
    val suite = definition.fullyQualifiedName()
    val outcome = group.outcomeFor(suite, loggers)
    val own = outcome.eventsFor(suite)
    own.foreach(test => handler.handle(new SuiteTestEvent(definition, test)))
    outcome.failure.foreach(error => handler.handle(new SuiteErrorEvent(definition, error)))
    trace(s"PROJECTION_EMIT suite=$suite events=${own.size} error=${outcome.failure.isDefined} thread=${Thread.currentThread().getName}")
    Array.empty[TestTask]
  }
}

final class SuiteTestEvent(definition: TaskDef, test: NestedEvent) extends Event {
  def fullyQualifiedName(): String = definition.fullyQualifiedName()
  def fingerprint(): Fingerprint = definition.fingerprint()
  def selector(): Selector = new TestSelector(test.testName)
  def status(): Status = test.source.status()
  def throwable(): OptionalThrowable = test.source.throwable()
  def duration(): Long = test.source.duration()
}

final class SuiteErrorEvent(definition: TaskDef, error: Throwable) extends Event {
  def fullyQualifiedName(): String = definition.fullyQualifiedName()
  def fingerprint(): Fingerprint = definition.fingerprint()
  def selector(): Selector = new SuiteSelector
  def status(): Status = Status.Error
  def throwable(): OptionalThrowable = new OptionalThrowable(error)
  def duration(): Long = 0L
}

final class CandidateControl {
  private val listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
  val port: Int = listener.getLocalPort
  private var connection = Option.empty[java.net.Socket]
  private var failure = Option.empty[Throwable]
  private var run = Option.empty[RunId]
  private val receiver = new Thread(() => {
    try {
      val accepted = listener.accept()
      synchronized { connection = Some(accepted); notifyAll() }
    } catch {
      case NonFatal(cause) => synchronized { failure = Some(cause); notifyAll() }
    }
  }, "candidate-control-accept")
  receiver.start()
  def started(value: RunId): Unit = synchronized { require(run.isEmpty, "Duplicate target start"); run = Some(value) }
  def cancel(): Unit = synchronized {
    while (connection.isEmpty && failure.isEmpty) wait()
    failure.foreach(throw _)
    val selected = run.getOrElse(throw new IllegalStateException("Target has not started"))
    val socket = connection.getOrElse(throw new IllegalStateException("Target input connection is absent"))
    val bytes = (ProtocolCodec.encode(ProtocolMessage.Cancel(selected)) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)
    socket.getOutputStream.write(bytes)
    socket.getOutputStream.flush()
    println("TARGET_CANCEL_SENT run=" + selected.value)
  }
  def close(): Unit = {
    listener.close()
    synchronized { connection.foreach(_.close()) }
    receiver.join()
  }
}
