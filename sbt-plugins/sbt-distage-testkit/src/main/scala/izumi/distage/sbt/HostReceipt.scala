package izumi.distage.sbt

import izumi.distage.testkit.protocol.{FileForkReceiptStore, FileForkRunReports, ForkProcessId, ForkReceiptArguments, ForkReceiptCounts, ForkReceiptSuite, ForkReceiptSummary}
import izumi.distage.sbt.target.{ForeignRunReports, TaskCompleteness, TaskGroups}

import sbt._
import sbt.testing.{AnnotatedFingerprint, Event, Fingerprint, NestedSuiteSelector, NestedTestSelector, Status, SubclassFingerprint, SuiteSelector, TestSelector, TestWildcardSelector}

import scala.util.control.NonFatal
import scala.jdk.CollectionConverters._

private[sbt] final case class HostSuiteName(value: String)
private[sbt] sealed trait HostEventSelector
private[sbt] object HostEventSelector {
  case object Suite extends HostEventSelector
  final case class Test(name: String) extends HostEventSelector
  final case class NestedSuite(name: String) extends HostEventSelector
  final case class NestedTest(suite: String, name: String) extends HostEventSelector
  final case class Wildcard(value: String) extends HostEventSelector
}
private[sbt] sealed trait HostEventFingerprint
private[sbt] object HostEventFingerprint {
  final case class Subclass(module: Boolean, name: String, noArgs: Boolean) extends HostEventFingerprint
  final case class Annotation(module: Boolean, name: String) extends HostEventFingerprint
  def from(value: Fingerprint): HostEventFingerprint = value match {
    case subclass: SubclassFingerprint => Subclass(subclass.isModule(), subclass.superclassName(), subclass.requireNoArgConstructor())
    case annotation: AnnotatedFingerprint => Annotation(annotation.isModule(), annotation.annotationName())
    case other => throw new IllegalArgumentException("Unsupported event fingerprint: " + other)
  }
}
private[sbt] final case class HostEventFailure(className: String, message: String) {
  def matches(actual: HostEventFailure): Boolean = actual.message == message || actual.message == className + ": " + message
}
private[sbt] final case class HostEventIdentity(name: String, fingerprint: HostEventFingerprint, selector: HostEventSelector, status: Status, duration: Long, failure: Option[HostEventFailure]) {
  def matches(actual: HostEventIdentity): Boolean = {
    name == actual.name && fingerprint == actual.fingerprint && selector == actual.selector && status == actual.status && duration == actual.duration &&
      ((failure, actual.failure) match {
        case (Some(expected), Some(received)) => expected.matches(received)
        case (None, None) => true
        case _ => false
      })
  }
}
private[sbt] object HostEventIdentity {
  def from(event: Event): HostEventIdentity = {
    val selector = event.selector() match {
      case _: SuiteSelector => HostEventSelector.Suite
      case test: TestSelector => HostEventSelector.Test(test.testName())
      case nested: NestedSuiteSelector => HostEventSelector.NestedSuite(nested.suiteId())
      case nested: NestedTestSelector => HostEventSelector.NestedTest(nested.suiteId(), nested.testName())
      case wildcard: TestWildcardSelector => HostEventSelector.Wildcard(wildcard.testWildcard())
      case other => throw new IllegalArgumentException("Unsupported event selector: " + other)
    }
    val failure = if (event.throwable().isDefined) {
      val cause = event.throwable().get()
      Some(HostEventFailure(cause.getClass.getName, cause.getMessage))
    } else None
    HostEventIdentity(event.fullyQualifiedName(), HostEventFingerprint.from(event.fingerprint()), selector, event.status(), event.duration(), failure)
  }
}
private[sbt] final case class HostGroupIdentity(counts: HostSuiteCounts, events: Vector[HostEventIdentity]) {
  def matches(actual: HostGroupIdentity): Boolean = {
    var remaining = events
    counts == actual.counts && actual.events.forall { event =>
      val index = remaining.indexWhere(_.matches(event))
      if (index < 0) false else { remaining = remaining.patch(index, Nil, 1); true }
    } && remaining.isEmpty
  }
}
private[sbt] final case class HostGroupReceipt(name: HostSuiteName, result: SuiteResult, events: Vector[HostEventIdentity])

private[sbt] final case class HostSuiteCounts(result: TestResult, passed: Int, failed: Int, errors: Int, skipped: Int, ignored: Int, canceled: Int, pending: Int) {
  def +(other: HostSuiteCounts): HostSuiteCounts = HostSuiteCounts(
    HostSuiteCounts.overall(result, other.result), passed + other.passed, failed + other.failed, errors + other.errors,
    skipped + other.skipped, ignored + other.ignored, canceled + other.canceled, pending + other.pending,
  )
}

private[sbt] object HostSuiteCounts {
  val empty: HostSuiteCounts = HostSuiteCounts(TestResult.Passed, 0, 0, 0, 0, 0, 0, 0)
  def from(value: SuiteResult): HostSuiteCounts = HostSuiteCounts(
    value.result, value.passedCount, value.failureCount, value.errorCount, value.skippedCount, value.ignoredCount, value.canceledCount, value.pendingCount,
  )
  def from(record: TaskCompleteness.Completion): HostSuiteCounts = {
    val counts = record.counts()
    val result = if (counts.error() > 0 || !record.returnedNormally()) TestResult.Error else if (counts.failure() > 0) TestResult.Failed else TestResult.Passed
    HostSuiteCounts(result, counts.success(), counts.failure(), counts.error(), counts.skipped(), counts.ignored(), counts.canceled(), counts.pending())
  }
  def overall(first: TestResult, second: TestResult): TestResult = {
    if (first == TestResult.Error || second == TestResult.Error) TestResult.Error
    else if (first == TestResult.Failed || second == TestResult.Failed) TestResult.Failed
    else TestResult.Passed
  }
}

private[sbt] final class HostReceipt(taskGroups: TaskGroups.Store) {
  private var owned = Set.empty[HostSuiteName]
  private var expected = Set.empty[HostSuiteName]
  private var starts = Map.empty[HostSuiteName, Int]
  private var ends = Map.empty[HostSuiteName, Int]
  private var activeGroups = Map.empty[Thread, HostGroupReceipt]
  private var groupResults = Map.empty[HostSuiteName, Vector[SuiteResult]]
  private var groupIdentities = Map.empty[HostSuiteName, Vector[HostGroupIdentity]]
  private var received = Map.empty[HostSuiteName, HostSuiteCounts]
  private var delivered = HostSuiteCounts.empty
  private var completed = Option.empty[TestResult]
  private var publicationFailure = Option.empty[Throwable]
  private var interrupted = false
  private var closed = false

  def configure(names: Set[HostSuiteName]): TestsListener = synchronized {
    requireOpen()
    require(owned.isEmpty || owned == names, "Host receipt changed its suite catalogue")
    owned = names
    new TestsListener {
      override def doInit(): Unit = ()
      override def startGroup(name: String): Unit = start(HostSuiteName(name))
      override def testEvent(event: TestEvent): Unit = record(event)
      override def endGroup(name: String, cause: Throwable): Unit = end(HostSuiteName(name), TestResult.Error)
      override def endGroup(name: String, result: TestResult): Unit = end(HostSuiteName(name), result)
      override def doComplete(result: TestResult): Unit = complete(result)
    }
  }

  def expect(name: HostSuiteName): Unit = synchronized {
    requireOpen()
    expected += name
  }

  private def start(name: HostSuiteName): Unit = synchronized {
    requireOpen()
    val thread = Thread.currentThread()
    require(!activeGroups.contains(thread), "Host listener thread started an overlapping group")
    activeGroups = activeGroups.updated(thread, HostGroupReceipt(name, SuiteResult.Empty, Vector.empty))
    starts = starts.updated(name, starts.getOrElse(name, 0) + 1)
    if (owned.contains(name)) {
      if (!received.contains(name)) received = received.updated(name, HostSuiteCounts.empty)
    }
  }

  private def record(event: TestEvent): Unit = synchronized {
    requireOpen()
    val thread = Thread.currentThread()
    val group = activeGroups.getOrElse(thread, throw new IllegalStateException("Host event has no active listener group"))
    val identities = event.detail.iterator.map(HostEventIdentity.from).toVector
    activeGroups = activeGroups.updated(thread, group.copy(result = group.result + SuiteResult(event.detail), events = group.events ++ identities))
    delivered = delivered + HostSuiteCounts.from(SuiteResult(event.detail))
    event.detail.groupBy(detail => HostSuiteName(detail.fullyQualifiedName())).foreach { case (name, details) =>
      if (owned.contains(name)) {
        if (details.exists(detail => detail.throwable().isDefined && detail.throwable().get().isInstanceOf[InterruptedException])) interrupted = true
        val counts = HostSuiteCounts.from(SuiteResult(details))
        received = received.updated(name, received.getOrElse(name, HostSuiteCounts.empty) + counts)
      }
    }
  }

  private def end(name: HostSuiteName, result: TestResult): Unit = synchronized {
    requireOpen()
    val thread = Thread.currentThread()
    val group = activeGroups.getOrElse(thread, throw new IllegalStateException("Host group ended without starting"))
    require(group.name == name, "Host listener ended a different group")
    activeGroups -= thread
    val counts = group.result
    val finished = new SuiteResult(HostSuiteCounts.overall(counts.result, result), counts.passedCount, counts.failureCount, counts.errorCount, counts.skippedCount, counts.ignoredCount, counts.canceledCount, counts.pendingCount, counts.throwables)
    groupResults = groupResults.updated(name, groupResults.getOrElse(name, Vector.empty) :+ finished)
    groupIdentities = groupIdentities.updated(name, groupIdentities.getOrElse(name, Vector.empty) :+ HostGroupIdentity(HostSuiteCounts.from(finished), group.events))
    ends = ends.updated(name, ends.getOrElse(name, 0) + 1)
    delivered = delivered.copy(result = HostSuiteCounts.overall(delivered.result, result))
    if (owned.contains(name)) {
      val counts = received.getOrElse(name, HostSuiteCounts.empty)
      received = received.updated(name, counts.copy(result = HostSuiteCounts.overall(counts.result, result)))
    }
  }

  private def complete(result: TestResult): Unit = synchronized {
    requireOpen()
    completed = Some(completed.fold(result)(HostSuiteCounts.overall(_, result)))
  }

  def forkSummary(name: HostSuiteName): Option[ForkReceiptSummary] = synchronized {
    requireOpen()
    if (owned.contains(name)) {
      val counts = received.getOrElse(name, throw new IllegalStateException("Completed host group has no event counts"))
      Some(ForkReceiptSummary(ends.getOrElse(name, throw new IllegalStateException("Host group has not completed")),
        ForkReceiptCounts(counts.passed, counts.failed, counts.errors, counts.skipped, counts.ignored, counts.canceled, counts.pending)))
    } else None
  }

  def verifyCompletion(): Unit = synchronized {
    closed = true
    verifyPublication()
    if (expected != starts.keySet.intersect(owned) || starts != ends || (starts.nonEmpty && completed.isEmpty) ||
      (completed.contains(TestResult.Passed) && delivered.result != TestResult.Passed)) {
      throw new MessageOnlyException(s"Incomplete distage host completion: selected=$expected started=$starts completed=$ends received=$received delivered=$delivered overall=$completed")
    }
  }

  def close(): Unit = synchronized { closed = true }

  def isInterrupted: Boolean = synchronized { interrupted }

  def owns(name: HostSuiteName): Boolean = synchronized { owned.contains(name) }

  def selected: Set[HostSuiteName] = synchronized { expected }

  def cancel(): Unit = synchronized { interrupted = true }

  def unreported(name: HostSuiteName): Boolean = synchronized {
    requireOpen()
    require(starts.getOrElse(name, 0) == ends.getOrElse(name, 0), "Fork cancellation overlaps an active host report group")
    ends.getOrElse(name, 0) == 0
  }

  def completedGroups(names: Set[HostSuiteName]): Map[HostSuiteName, Vector[HostGroupIdentity]] = synchronized {
    requireOpen()
    require(names.forall(name => starts.getOrElse(name, 0) == ends.getOrElse(name, 0)), "Fork cancellation overlaps an active host report group")
    groupIdentities.filter { case (name, _) => names.contains(name) }
  }

  def normalise(output: Tests.Output): Tests.Output = synchronized {
    requireOpen()
    verifyPublication()
    var events = output.events
    resultGroups.foreach { case (name, groups) =>
      if (groups.size > 1) {
        val merged = groups.foldLeft(SuiteResult.Empty)(_ + _)
        val original = events.getOrElse(name.value, throw new MessageOnlyException("Missing SDK result for completed duplicate group: " + name.value))
        if (HostSuiteCounts.from(original) != HostSuiteCounts.from(merged)) {
          if (!groups.exists(group => HostSuiteCounts.from(group) == HostSuiteCounts.from(original))) throw new MessageOnlyException("SDK duplicate group result differs from every completed group: " + name.value)
          events = events.updated(name.value, merged)
        }
      }
    }
    if (events eq output.events) output else output.copy(events = events)
  }

  def verify(output: Tests.Output): Unit = synchronized {
    requireOpen()
    closed = true
    verifyPublication()
    val actual = output.events.collect { case (name, counts) if owned.contains(HostSuiteName(name)) => HostSuiteName(name) -> HostSuiteCounts.from(counts) }
    val returned = output.events.values.foldLeft(HostSuiteCounts.empty)((counts, result) => counts + HostSuiteCounts.from(result))
    val groups = resultGroups.view.mapValues(values => HostSuiteCounts.from(values.foldLeft(SuiteResult.Empty)(_ + _))).toMap
    val allActual = output.events.map { case (name, counts) => HostSuiteName(name) -> HostSuiteCounts.from(counts) }
    if (expected != starts.keySet.intersect(owned) || starts != ends || actual != received ||
      allActual != groups || activeGroups.nonEmpty ||
      output.events.keySet.map(HostSuiteName(_)) != starts.keySet.map(resultName) || returned != delivered ||
      (output.overall == TestResult.Passed && delivered.result != TestResult.Passed)) {
      throw new MessageOnlyException(s"Incomplete distage host result: selected=$expected started=$starts completed=$ends received=$received returned=$actual delivered=$delivered returnedCounts=$returned overall=${output.overall}")
    }
  }

  private def resultName(group: HostSuiteName): HostSuiteName = {
    val matches = taskGroups.mappings().asScala.filter(_.listener().value() == group.value).map(value => HostSuiteName(value.result().value())).toSet
    require(matches.size <= 1, "Listener group has conflicting task result identities: " + group.value)
    matches.headOption.getOrElse(group)
  }

  private def resultGroups: Map[HostSuiteName, Vector[SuiteResult]] =
    groupResults.toVector.groupBy { case (name, _) => resultName(name) }.map { case (name, groups) => name -> groups.flatMap(_._2) }

  private def requireOpen(): Unit = require(!closed, "Host event emitted after receipt completion")

  def verifyTargetCompletion(directory: java.nio.file.Path): Unit = synchronized {
    val records = new TaskCompleteness.FileCompletionStore(directory).completed().asScala.toVector
    val bySuite = records.groupBy(value => HostSuiteName(value.suite().value()))
    val actual = bySuite.map { case (name, completed) =>
      val counts = completed.foldLeft(HostSuiteCounts.empty)((sum, record) => sum + HostSuiteCounts.from(record))
      name -> counts
    }
    if (bySuite.keySet != expected || bySuite.exists { case (name, completed) => completed.size != ends.getOrElse(name, 0) || completed.exists(value => !value.returnedNormally()) } || actual != received) {
      throw new MessageOnlyException(s"Incomplete distage target suite terminal records: selected=$expected completed=${bySuite.map { case (name, values) => name -> values.size }} received=$received target=$actual")
    }
  }

  def failPublication(cause: Throwable): Unit = synchronized {
    requireOpen()
    if (publicationFailure.isEmpty) publicationFailure = Some(cause)
  }

  private def verifyPublication(): Unit = publicationFailure.foreach { cause =>
    val failure = new MessageOnlyException("Incomplete distage fork acknowledgement: " + cause.getMessage)
    val _ = failure.initCause(cause)
    throw failure
  }
}

private[sbt] final class HostReceiptGeneration(val receipt: HostReceipt, val store: FileForkReceiptStore) {
  val completion = new HostForkCompletion(store.directory)
  var reports = Option.empty[HostForkReports]
  var foreignReports = Option.empty[HostForeignForkReports]
}

private[sbt] final class HostReceiptOwner(createStore: () => FileForkReceiptStore, hostProcess: ForkProcessId) {
  private var current = Option.empty[HostReceiptGeneration]
  private var pending = Vector.empty[HostReceiptGeneration]

  def enter(): HostReceiptGeneration = synchronized {
    require(current.isEmpty, "Overlapping distage host task admission")
    val store = createStore()
    val generation = new HostReceiptGeneration(new HostReceipt(new TaskGroups.FileStore(store.directory)), store)
    current = Some(generation)
    pending :+= generation
    generation
  }

  def receipt: HostReceipt = synchronized { current.getOrElse(throw new IllegalStateException("Distage host task has no active receipt")).receipt }

  def store: FileForkReceiptStore = synchronized { current.getOrElse(throw new IllegalStateException("Distage host task has no active receipt")).store }
  def completion: HostForkCompletion = synchronized { current.getOrElse(throw new IllegalStateException("Distage host task has no active receipt")).completion }
  def hasPending: Boolean = synchronized { pending.nonEmpty }
  def hasInterruption: Boolean = {
    val generations = synchronized { pending }
    generations.exists(_.receipt.isInterrupted)
  }

  def configureReports(definitions: Seq[TestDefinition], listeners: Seq[TestReportListener]): Unit = synchronized {
    val generation = current.getOrElse(throw new IllegalStateException("Distage host task has no active receipt"))
    generation.reports = Some(new HostForkReports(new FileForkRunReports(generation.store.directory), new TaskCompleteness.FileCompletionStore(generation.store.directory), definitions, listeners, generation.receipt, hostProcess))
    generation.foreignReports = Some(new HostForeignForkReports(new ForeignRunReports.FileStore(generation.store.directory), listeners, generation.receipt))
  }

  def requestForkCancellation(): Unit = {
    val generations = synchronized { pending }
    generations.foreach { generation =>
      if (generation.completion.cancel().nonEmpty) generation.receipt.cancel()
    }
  }

  def completeForkCancellation(): Unit = {
    val generations = synchronized { pending }
    generations.filter(_.receipt.isInterrupted).foreach { generation =>
      val admissions = generation.completion.cancel()
      if (admissions.nonEmpty) {
        generation.reports.getOrElse(throw new IllegalStateException("Admitted fork has no host reports")).cancel(admissions)
        generation.completion.awaitShutdown(admissions)
        generation.foreignReports.getOrElse(throw new IllegalStateException("Admitted fork has no foreign host reports")).cancel(admissions)
      }
    }
  }

  def consume(output: Tests.Output): Tests.Output = {
    val generation = synchronized {
      val active = current.getOrElse(throw new IllegalStateException("Distage host task has no active receipt"))
      current = None
      active
    }
    val normalised = generation.receipt.normalise(output)
    generation.receipt.verify(normalised)
    normalised
  }

  def abort(generation: HostReceiptGeneration): Unit = {
    synchronized { if (current.contains(generation)) current = None }
    generation.receipt.close()
    generation.store.close()
    synchronized { pending = pending.filterNot(_ eq generation) }
  }

  def finishCommand(): Unit = {
    val unfinished = synchronized { pending }
    if (unfinished.nonEmpty) {
      val failure = new MessageOnlyException("Incomplete distage host command completion")
      unfinished.foreach { generation =>
        try generation.completion.finish(commit = false)
        catch { case cause: Throwable => failure.addSuppressed(cause) }
        finally {
          try abort(generation)
          catch { case cause: Throwable => failure.addSuppressed(cause) }
        }
      }
      throw failure
    }
  }

  private def finish[A](generation: HostReceiptGeneration)(operation: => A): A = {
    var original = Option.empty[Throwable]
    try {
      val result = operation
      generation.receipt.verifyTargetCompletion(generation.store.directory)
      generation.completion.finish(commit = true)
      result
    }
    catch { case cause: Throwable => original = Some(cause); throw cause }
    finally {
      if (original.nonEmpty) {
        try generation.completion.finish(commit = false)
        catch { case cause: Throwable => original.get.addSuppressed(cause) }
      }
      HostFailures.cleanup(original)(abort(generation))
    }
  }

  def input[A](task: Task[A]): Task[A] = sbt.std.TaskExtra.task { enter() }.flatMap { generation =>
    task.result.map { result =>
      finish(generation) { result.toEither.fold(cause => throw cause, value => { generation.receipt.verifyCompletion(); value }) }
    }
  }

  def output(task: Task[Tests.Output]): Task[Tests.Output] = sbt.std.TaskExtra.task { enter() }.flatMap { generation =>
    task.result.map { result =>
      finish(generation) { result.toEither.fold(cause => throw cause, consume) }
    }
  }
}

private[sbt] final class HostForkReceiptListener(inherited: TestsListener, receipt: HostReceipt, store: FileForkReceiptStore) extends TestsListener {
  override def doInit(): Unit = inherited.doInit()
  override def startGroup(name: String): Unit = inherited.startGroup(name)
  override def testEvent(event: TestEvent): Unit = inherited.testEvent(event)
  override def endGroup(name: String, cause: Throwable): Unit = { inherited.endGroup(name, cause); publish(name) }
  override def endGroup(name: String, result: TestResult): Unit = { inherited.endGroup(name, result); publish(name) }
  override def doComplete(result: TestResult): Unit = inherited.doComplete(result)
  private def publish(name: String): Unit = {
    try receipt.forkSummary(HostSuiteName(name)).foreach(summary => store.publish(ForkReceiptSuite(name), summary))
    catch {
      case NonFatal(cause) => receipt.failPublication(cause); throw cause
    }
  }
}

private[sbt] final class HostSelectionObserver(val inherited: Seq[String] => Seq[String => Boolean], names: Set[HostSuiteName], owner: HostReceiptOwner)
  extends (Seq[String] => Seq[String => Boolean]) {
  override def apply(arguments: Seq[String]): Seq[String => Boolean] = {
    val receipt = owner.receipt
    HostReceiptPolicy.unobserved(inherited)(arguments).map { filter => (name: String) =>
      val included = filter(name)
      if (included && names.contains(HostSuiteName(name))) receipt.expect(HostSuiteName(name))
      included
    }
  }
}

private[sbt] final class HostResultLogger(val inherited: TestResultLogger, owner: HostReceiptOwner) extends TestResultLogger {
  override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
    val (verified, normalised) = log match {
      case previous: HostVerifiedResultLog if previous.output eq output => previous -> output
      case other =>
        val normalised = owner.consume(output)
        new HostVerifiedResultLog(other, normalised) -> normalised
    }
    inherited.run(verified, normalised, taskName)
  }
}

private[sbt] final class HostVerifiedResultLog(inherited: sbt.util.Logger, val output: Tests.Output) extends sbt.util.Logger {
  override def trace(cause: => Throwable): Unit = inherited.trace(cause)
  override def success(message: => String): Unit = inherited.success(message)
  override def log(level: sbt.util.Level.Value, message: => String): Unit = inherited.log(level, message)
}

private[sbt] object HostReceiptPolicy {
  val owner: SettingKey[HostReceiptOwner] = settingKey[HostReceiptOwner]("Distage host task receipt owner")
  val normalisedLogger: SettingKey[TestResultLogger] = settingKey[TestResultLogger]("Result logger receiving reconciled group results")
  val normalisedFilter: TaskKey[Seq[String] => Seq[String => Boolean]] = taskKey[Seq[String] => Seq[String => Boolean]]("Selection filter observed at the active input boundary")

  def ownerAt(parent: File): HostReceiptOwner = new HostReceiptOwner(() => FileForkReceiptStore.create((parent / "distage-fork-receipts").toPath.toAbsolutePath), ForkProcessId(ProcessHandle.current().pid()))

  def names(definitions: Seq[TestDefinition]): Set[HostSuiteName] = definitions.filter(DistageHostPolicy.isDistage).map(value => HostSuiteName(value.name)).toSet

  def unobserved(selection: Seq[String] => Seq[String => Boolean]): Seq[String] => Seq[String => Boolean] = selection match {
    case observed: HostSelectionObserver => unobserved(observed.inherited)
    case observed: HostReportedSelection => unobserved(observed.inherited)
    case other => other
  }

  def execution(inherited: Tests.Execution, definitions: Seq[TestDefinition], frameworks: Seq[TestFramework], owner: HostReceiptOwner, full: Boolean, reportFormat: HostJUnitFileFormat): Tests.Execution = {
    val owned = names(definitions)
    val receipt = owner.receipt
    val listener = new HostForkReceiptListener(receipt.configure(owned), receipt, owner.store)
    val inheritedOptions = inherited.options.flatMap {
      case Tests.Listeners(listeners) => Some(Tests.Listeners(listeners.filterNot(_.isInstanceOf[HostForkReceiptListener]).map(listener => HostJUnitReports.wrap(listener, receipt, reportFormat))))
      case Tests.Argument(Some(framework), values) if framework == DistageHostPolicy.framework && values.headOption.contains(ForkReceiptArguments.HostDirectoryOption) => None
      case Tests.Argument(_, values) if values.headOption.contains(TaskGroups.OPTION) => None
      case other => Some(other)
    }
    val options = if (full) {
      def capture(filter: String => Boolean): String => Boolean = name => {
        val included = filter(name)
        if (included && owned.contains(HostSuiteName(name))) receipt.expect(HostSuiteName(name))
        included
      }
      val wrapped = inheritedOptions.map {
        case Tests.Filters(includes) => Tests.Filters(includes.map(capture))
        case other => other
      }
      if (wrapped.exists { case Tests.Filters(includes) => includes.nonEmpty; case _ => false }) wrapped
      else wrapped :+ Tests.Filters(Seq(capture(_ => true)))
    } else inheritedOptions
    val directory = Tests.Argument(DistageHostPolicy.framework, ForkReceiptArguments.HostDirectoryOption, owner.store.directory.toString, ForkReceiptArguments.CommandCompletionOption)
    val groups = frameworks.map(framework => Tests.Argument(framework, TaskGroups.OPTION, owner.store.directory.toString))
    val execution = inherited.copy(options = Tests.Listeners(Seq(listener)) +: (options ++ groups :+ directory))
    owner.configureReports(definitions, execution.options.collect { case Tests.Listeners(values) => values }.flatten)
    execution
  }

  def logger(inherited: TestResultLogger, owner: HostReceiptOwner): TestResultLogger = inherited match {
    case observed: HostResultLogger => logger(observed.inherited, owner)
    case other => new HostResultLogger(other, owner)
  }
}
