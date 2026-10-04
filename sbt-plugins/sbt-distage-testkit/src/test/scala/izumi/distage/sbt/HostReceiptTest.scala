package izumi.distage.sbt

import izumi.distage.testkit.protocol.{FileForkReceiptStore, ForkReceiptArguments, ForkReceiptCounts, ForkReceiptSuite, ForkReceiptSummary}

import sbt._
import sbt.testing.{Event, Fingerprint, OptionalThrowable, Selector, Status, SubclassFingerprint, SuiteSelector}

import java.nio.file.{Files, Paths}

object HostReceiptTest {
  private val name = HostSuiteName("fixture.OwnedSuite")
  private val empty = Tests.Output(TestResult.Passed, Map.empty, Nil)

  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Receipt checks require a new owned fixture directory")
    val parent = Paths.get(arguments.head).toAbsolutePath
    require(!Files.exists(parent), "Receipt fixture directory must be new")
    val _ = Files.createDirectories(parent)
    def owner(): HostReceiptOwner = new HostReceiptOwner(() => FileForkReceiptStore.create(parent))
    check("reject a selected suite omitted by the target") {
      val receipt = new HostReceipt
      receipt.configure(Set(name))
      receipt.expect(name)
      rejects(classOf[MessageOnlyException])(receipt.verify(empty))
    }

    check("retain completion validation when the result logger is replaced") {
      val omitted = new HostReceipt
      omitted.expect(name)
      omitted.configure(Set(name)).doComplete(TestResult.Passed)
      rejects(classOf[MessageOnlyException])(omitted.verifyCompletion())
      val complete = new HostReceipt
      val listener = complete.configure(Set(name))
      complete.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Success))))
      listener.endGroup(name.value, TestResult.Passed)
      listener.doComplete(TestResult.Passed)
      complete.verifyCompletion()
    }

    check("reject a passed SDK completion after a received error") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      receipt.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Error))))
      listener.endGroup(name.value, TestResult.Error)
      listener.doComplete(TestResult.Passed)
      rejects(classOf[MessageOnlyException])(receipt.verifyCompletion())
    }

    check("reject success events without group completion") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      receipt.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Success))))
      rejects(classOf[MessageOnlyException])(receipt.verify(output(new SuiteResult(TestResult.Passed, 1, 0, 0, 0, 0, 0, 0))))
    }

    check("reconcile each status without treating equal totals as equal outcomes") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      receipt.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(Status.Success, Status.Failure, Status.Error, Status.Skipped, Status.Ignored, Status.Canceled, Status.Pending).map(event(name, _))))
      listener.endGroup(name.value, TestResult.Error)
      receipt.verify(output(new SuiteResult(TestResult.Error, 1, 1, 1, 1, 1, 1, 1)))

      val alias = new HostReceipt
      val aliasListener = alias.configure(Set(name))
      alias.expect(name)
      aliasListener.startGroup(name.value)
      aliasListener.testEvent(TestEvent(Seq(event(name, Status.Success))))
      aliasListener.endGroup(name.value, TestResult.Passed)
      rejects(classOf[MessageOnlyException])(alias.verify(output(new SuiteResult(TestResult.Error, 0, 0, 1, 0, 0, 0, 0))))
    }

    check("preserve a zero-event group failure") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      receipt.expect(name)
      listener.startGroup(name.value)
      listener.endGroup(name.value, new IllegalStateException("group failure"))
      receipt.verify(output(SuiteResult.Error))
    }

    check("accept empty selection and completed zero-event suites") {
      val receipt = new HostReceipt
      receipt.configure(Set(name))
      receipt.verify(empty)
      val zero = new HostReceipt
      val listener = zero.configure(Set(name))
      zero.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Nil))
      listener.endGroup(name.value, TestResult.Passed)
      zero.verify(output(new SuiteResult(TestResult.Passed, 0, 0, 0, 0, 0, 0, 0)))
      rejects(classOf[IllegalArgumentException])(listener.startGroup(name.value))
    }

    check("leave foreign framework results unchanged") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      val foreign = HostSuiteName("fixture.ForeignSuite")
      listener.startGroup(foreign.value)
      listener.testEvent(TestEvent(Seq(event(foreign, Status.Error))))
      listener.endGroup(foreign.value, TestResult.Error)
      receipt.verify(Tests.Output(TestResult.Error, Map(foreign.value -> SuiteResult.Error), Nil))
    }

    check("retain a newer admission when an earlier task aborts") {
      val current = owner()
      val first = current.enter()
      rejects(classOf[IllegalArgumentException]) { val _ = current.enter(); () }
      current.consume(empty)
      val second = current.enter()
      require(first.store.directory != second.store.directory, "New admission reused an earlier directory")
      current.abort(first)
      assert(current.receipt eq second.receipt)
      require(!Files.exists(first.store.directory) && Files.isDirectory(second.store.directory), "Earlier cleanup affected a newer admission")
      current.consume(empty)
      current.abort(second)
      rejects(classOf[IllegalStateException]) { val _ = current.receipt; () }
    }

    check("rebind inherited selection observers to the current configuration") {
      val inheritedOwner = owner()
      val inherited = new HostSelectionObserver(arguments => Seq(candidate => arguments.contains(candidate)), Set(name), inheritedOwner)
      val current = owner()
      val generation = current.enter()
      val listener = current.receipt.configure(Set(name))
      val selection = new HostSelectionObserver(inherited, Set(name), current)(Seq(name.value))
      assert(!selection.head("fixture.ExcludedSuite"))
      assert(selection.head(name.value))
      listener.startGroup(name.value)
      listener.endGroup(name.value, TestResult.Passed)
      current.consume(output(new SuiteResult(TestResult.Passed, 0, 0, 0, 0, 0, 0, 0)))
      current.abort(generation)
    }

    check("publish exact fork receipts only after completed host groups") {
      val current = owner()
      val generation = current.enter()
      val reader = FileForkReceiptStore.open(generation.store.directory)
      val listener = new HostForkReceiptListener(current.receipt.configure(Set(name)), current.receipt, generation.store)
      val suite = ForkReceiptSuite(name.value)
      current.receipt.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Success))))
      assert(reader.received(suite).isEmpty)
      listener.endGroup(name.value, TestResult.Passed)
      assert(reader.received(suite).contains(ForkReceiptSummary(1, ForkReceiptCounts(1, 0, 0, 0, 0, 0, 0))))
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Error))))
      listener.endGroup(name.value, TestResult.Error)
      assert(reader.received(suite).contains(ForkReceiptSummary(2, ForkReceiptCounts(1, 0, 1, 0, 0, 0, 0))))
      listener.startGroup("fixture.ForeignSuite")
      listener.endGroup("fixture.ForeignSuite", TestResult.Passed)
      assert(reader.received(ForkReceiptSuite("fixture.ForeignSuite")).isEmpty)
      current.consume(output(new SuiteResult(TestResult.Error, 1, 0, 1, 0, 0, 0, 0)))
      current.abort(generation)
      require(!Files.exists(generation.store.directory), "Owned fork directory survived cleanup")
    }

    check("replace inherited fork options and listeners with current ownership") {
      val first = owner()
      val previous = first.enter()
      val second = owner()
      val current = second.enter()
      val earlier = new HostForkReceiptListener(first.receipt.configure(Set.empty), first.receipt, previous.store)
      val foreign = new TestsListener {
        override def doInit(): Unit = ()
        override def startGroup(name: String): Unit = ()
        override def testEvent(event: TestEvent): Unit = ()
        override def endGroup(name: String, cause: Throwable): Unit = ()
        override def endGroup(name: String, result: TestResult): Unit = ()
        override def doComplete(result: TestResult): Unit = ()
      }
      val inherited = Tests.Execution(Seq(Tests.Listeners(Seq(earlier, foreign)), Tests.Argument(DistageHostPolicy.framework, ForkReceiptArguments.HostDirectoryOption, previous.store.directory.toString)), true, Seq.empty)
      val execution = HostReceiptPolicy.execution(inherited, Seq.empty, second, full = false)
      val listeners = execution.options.collect { case Tests.Listeners(values) => values }.flatten
      require(listeners.count(_.isInstanceOf[HostForkReceiptListener]) == 1 && listeners.contains(foreign) && !listeners.contains(earlier), "Inherited listeners were not rebound")
      val directories = execution.options.collect { case Tests.Argument(Some(framework), values) if framework == DistageHostPolicy.framework => values }
      require(directories == Seq(Seq(ForkReceiptArguments.HostDirectoryOption, current.store.directory.toString)), "Inherited receipt directory was retained")
      first.abort(previous); second.abort(current)
    }
    Files.delete(parent)
  }

  private def check(name: String)(body: => Unit): Unit = {
    body
    println("HOST_RECEIPT_CHECK_OK " + name)
  }

  private def rejects[A <: Throwable](expected: Class[A])(body: => Unit): Unit = {
    var observed = Option.empty[Throwable]
    try body catch { case scala.util.control.NonFatal(cause) => observed = Some(cause) }
    require(observed.exists(expected.isInstance), "Receipt counterexample did not produce " + expected.getName + ": " + observed)
  }

  private def output(counts: SuiteResult): Tests.Output = Tests.Output(counts.result, Map(name.value -> counts), Nil)

  private def event(suite: HostSuiteName, state: Status): Event = new Event {
    override def fullyQualifiedName(): String = suite.value
    override def fingerprint(): Fingerprint = new SubclassFingerprint {
      override def isModule(): Boolean = false
      override def superclassName(): String = "fixture.Spec"
      override def requireNoArgConstructor(): Boolean = true
    }
    override def selector(): Selector = new SuiteSelector
    override def status(): Status = state
    override def throwable(): OptionalThrowable = new OptionalThrowable
    override def duration(): Long = 0L
  }
}
