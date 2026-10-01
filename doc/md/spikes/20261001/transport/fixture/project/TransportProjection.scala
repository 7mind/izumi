import sbt._
import sbt.Keys._
import sbt.testing.{Event, EventHandler, Fingerprint, Framework, NestedTestSelector, OptionalThrowable, Runner, Selector, Status, SuiteSelector, TaskDef, TestSelector, Logger => TestLogger, Task => TestTask}
import scala.collection.mutable
import scala.util.control.NonFatal
import TransportSbtCompat._

/** SBT 1 has no `Def.uncached`; on SBT 2 the real member is selected before this extension. */
object TransportSbtCompat {
  implicit final class DefOps(private val definitions: Def.type) extends AnyVal {
    def uncached[A](value: A): A = value
  }
}

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
  val TargetFramework: TestFramework = TestFramework("transport.TransportFramework")

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
  val SecondGroupStart = "transport.SuiteD"

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
    new ProjectionRunner(platform.runner(arguments, remoteArguments, loader), SharingPolicy.fromArguments(arguments), trace)
}

/** Returns one ordinary host task per selected suite; each sharing group has one platform aggregate task. */
final class ProjectionRunner(platform: Runner, policy: SharingPolicy, trace: String => Unit) extends Runner {
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
      val shared = new SharedGroup(group, aggregate, trace)
      group.members.map(member => new SuiteTask(member, shared, trace): TestTask)
    }.toArray
}

final case class NestedEvent(testName: String, source: Event)

final class GroupOutcome(eventsBySuite: Map[String, Seq[NestedEvent]], val failure: Option[Throwable]) {
  def eventsFor(suite: String): Seq[NestedEvent] = eventsBySuite.getOrElse(suite, Nil)
}

/** Runs the aggregate on the first executing suite task's thread and memoizes its outcome, failure included. */
final class SharedGroup(group: SharingGroup, aggregate: TestTask, trace: String => Unit) {
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
    trace(s"PROJECTION_LAUNCH group=${group.label} suite=$suite thread=${Thread.currentThread().getName}")
    var result = new GroupOutcome(Map.empty, Some(new IllegalStateException(s"aggregate for group ${group.label} ended without an outcome")))
    try result = execute(loggers)
    finally publish(result)
    trace(s"PROJECTION_OUTCOME group=${group.label} failure=${result.failure.map(_.toString).getOrElse("none")}")
    result
  }

  private def execute(loggers: Array[TestLogger]): GroupOutcome = {
    val buffer = new SuiteEventBuffer(group.names.toSet)
    val failure =
      try {
        val nested = aggregate.execute(buffer, loggers)
        if (nested.isEmpty) None
        else Some(new IllegalStateException(s"aggregate for group ${group.label} returned ${nested.length} nested tasks"))
      } catch {
        case NonFatal(error) => Some(error)
      }
    buffer.close(failure)
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
final class SuiteEventBuffer(members: Set[String]) extends EventHandler {
  private val events = mutable.LinkedHashMap.empty[String, mutable.ArrayBuffer[NestedEvent]] // guarded by this
  private val violations = mutable.ArrayBuffer.empty[String] // guarded by this
  private var closed = false // guarded by this

  def handle(event: Event): Unit = synchronized {
    if (closed) throw new IllegalStateException(s"aggregate event after its task returned: ${event.fullyQualifiedName()} ${event.selector()}")
    event.selector() match {
      case nested: NestedTestSelector if members.contains(nested.suiteId()) =>
        events.getOrElseUpdate(nested.suiteId(), mutable.ArrayBuffer.empty[NestedEvent]) += NestedEvent(nested.testName(), event)
      case other =>
        violations += s"${event.fullyQualifiedName()} ${other}"
    }
  }

  def close(failure: Option[Throwable]): GroupOutcome = synchronized {
    closed = true
    val violation =
      if (violations.isEmpty) None
      else Some(new IllegalStateException(s"unattributable aggregate events: ${violations.mkString("; ")}"))
    new GroupOutcome(events.map { case (suite, buffered) => suite -> buffered.toList }.toMap, failure.orElse(violation))
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
