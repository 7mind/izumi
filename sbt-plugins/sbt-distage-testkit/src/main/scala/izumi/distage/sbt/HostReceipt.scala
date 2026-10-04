package izumi.distage.sbt

import sbt._

private[sbt] final case class HostSuiteName(value: String)

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
  def overall(first: TestResult, second: TestResult): TestResult = {
    if (first == TestResult.Error || second == TestResult.Error) TestResult.Error
    else if (first == TestResult.Failed || second == TestResult.Failed) TestResult.Failed
    else TestResult.Passed
  }
}

private[sbt] final class HostReceipt {
  private var owned = Set.empty[HostSuiteName]
  private var expected = Set.empty[HostSuiteName]
  private var starts = Map.empty[HostSuiteName, Int]
  private var ends = Map.empty[HostSuiteName, Int]
  private var received = Map.empty[HostSuiteName, HostSuiteCounts]
  private var completed = Option.empty[TestResult]
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
    if (owned.contains(name)) {
      starts = starts.updated(name, starts.getOrElse(name, 0) + 1)
      if (!received.contains(name)) received = received.updated(name, HostSuiteCounts.empty)
    }
  }

  private def record(event: TestEvent): Unit = synchronized {
    requireOpen()
    event.detail.groupBy(detail => HostSuiteName(detail.fullyQualifiedName())).foreach { case (name, details) =>
      if (owned.contains(name)) {
        val counts = HostSuiteCounts.from(SuiteResult(details))
        received = received.updated(name, received.getOrElse(name, HostSuiteCounts.empty) + counts)
      }
    }
  }

  private def end(name: HostSuiteName, result: TestResult): Unit = synchronized {
    requireOpen()
    if (owned.contains(name)) {
      ends = ends.updated(name, ends.getOrElse(name, 0) + 1)
      val counts = received.getOrElse(name, HostSuiteCounts.empty)
      received = received.updated(name, counts.copy(result = HostSuiteCounts.overall(counts.result, result)))
    }
  }

  private def complete(result: TestResult): Unit = synchronized {
    requireOpen()
    completed = Some(completed.fold(result)(HostSuiteCounts.overall(_, result)))
  }

  def verifyCompletion(): Unit = synchronized {
    closed = true
    if (expected != starts.keySet || starts != ends || (expected.nonEmpty && completed.isEmpty) ||
      (completed.contains(TestResult.Passed) && received.values.exists(_.result != TestResult.Passed))) {
      throw new MessageOnlyException(s"Incomplete distage host completion: selected=$expected started=$starts completed=$ends received=$received overall=$completed")
    }
  }

  def close(): Unit = synchronized { closed = true }

  def verify(output: Tests.Output): Unit = synchronized {
    requireOpen()
    closed = true
    val actual = output.events.collect { case (name, counts) if owned.contains(HostSuiteName(name)) => HostSuiteName(name) -> HostSuiteCounts.from(counts) }
    if (expected != starts.keySet || starts != ends || actual != received ||
      (output.overall == TestResult.Passed && received.values.exists(_.result != TestResult.Passed))) {
      throw new MessageOnlyException(s"Incomplete distage host result: selected=$expected started=$starts completed=$ends received=$received returned=$actual overall=${output.overall}")
    }
  }

  private def requireOpen(): Unit = require(!closed, "Host event emitted after receipt completion")
}

private[sbt] final class HostReceiptGeneration(val receipt: HostReceipt)

private[sbt] final class HostReceiptOwner {
  private var current = Option.empty[HostReceiptGeneration]

  def enter(): HostReceiptGeneration = synchronized {
    require(current.isEmpty, "Overlapping distage host task admission")
    val generation = new HostReceiptGeneration(new HostReceipt)
    current = Some(generation)
    generation
  }

  def receipt: HostReceipt = synchronized { current.getOrElse(throw new IllegalStateException("Distage host task has no active receipt")).receipt }

  def consume(output: Tests.Output): Unit = {
    val generation = synchronized {
      val active = current.getOrElse(throw new IllegalStateException("Distage host task has no active receipt"))
      current = None
      active
    }
    generation.receipt.verify(output)
  }

  def abort(generation: HostReceiptGeneration): Unit = {
    synchronized { if (current.contains(generation)) current = None }
    generation.receipt.close()
  }

  def input[A](task: Task[A]): Task[A] = sbt.std.TaskExtra.task { enter() }.flatMap { generation =>
    task.result.map { result =>
      try result.toEither.fold(cause => throw cause, value => { generation.receipt.verifyCompletion(); value })
      finally abort(generation)
    }
  }

  def output(task: Task[Tests.Output]): Task[Tests.Output] = sbt.std.TaskExtra.task { enter() }.flatMap { generation =>
    task.result.map { result =>
      try result.toEither.fold(cause => throw cause, value => { consume(value); value })
      finally abort(generation)
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
    owner.consume(output)
    inherited.run(log, output, taskName)
  }
}

private[sbt] object HostReceiptPolicy {
  val owner: SettingKey[HostReceiptOwner] = settingKey[HostReceiptOwner]("Distage host task receipt owner")

  def names(definitions: Seq[TestDefinition]): Set[HostSuiteName] = definitions.filter(DistageHostPolicy.isDistage).map(value => HostSuiteName(value.name)).toSet

  def unobserved(selection: Seq[String] => Seq[String => Boolean]): Seq[String] => Seq[String => Boolean] = selection match {
    case observed: HostSelectionObserver => unobserved(observed.inherited)
    case other => other
  }

  def execution(inherited: Tests.Execution, definitions: Seq[TestDefinition], owner: HostReceiptOwner, full: Boolean): Tests.Execution = {
    val owned = names(definitions)
    val receipt = owner.receipt
    val listener = receipt.configure(owned)
    val options = if (full) {
      def capture(filter: String => Boolean): String => Boolean = name => {
        val included = filter(name)
        if (included && owned.contains(HostSuiteName(name))) receipt.expect(HostSuiteName(name))
        included
      }
      val wrapped = inherited.options.map {
        case Tests.Filters(includes) => Tests.Filters(includes.map(capture))
        case other => other
      }
      if (wrapped.exists { case Tests.Filters(includes) => includes.nonEmpty; case _ => false }) wrapped
      else wrapped :+ Tests.Filters(Seq(capture(_ => true)))
    } else inherited.options
    inherited.copy(options = Tests.Listeners(Seq(listener)) +: options)
  }

  def logger(inherited: TestResultLogger, owner: HostReceiptOwner): TestResultLogger = inherited match {
    case observed: HostResultLogger => logger(observed.inherited, owner)
    case other => new HostResultLogger(other, owner)
  }
}
