package izumi.distage.sbt

import sbt._
import sbt.testing.{Event, Fingerprint, OptionalThrowable, Selector, Status, SubclassFingerprint, SuiteSelector}

object HostReceiptTest {
  private val name = HostSuiteName("fixture.OwnedSuite")
  private val empty = Tests.Output(TestResult.Passed, Map.empty, Nil)

  def main(arguments: Array[String]): Unit = {
    require(arguments.isEmpty, "Receipt checks do not accept arguments")
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
      val owner = new HostReceiptOwner
      val first = owner.enter()
      rejects(classOf[IllegalArgumentException]) { val _ = owner.enter(); () }
      owner.consume(empty)
      val second = owner.enter()
      owner.abort(first)
      assert(owner.receipt eq second.receipt)
      owner.consume(empty)
      owner.abort(second)
      rejects(classOf[IllegalStateException]) { val _ = owner.receipt; () }
    }

    check("rebind inherited selection observers to the current configuration") {
      val inheritedOwner = new HostReceiptOwner
      val inherited = new HostSelectionObserver(arguments => Seq(candidate => arguments.contains(candidate)), Set(name), inheritedOwner)
      val owner = new HostReceiptOwner
      owner.enter()
      val listener = owner.receipt.configure(Set(name))
      val selection = new HostSelectionObserver(inherited, Set(name), owner)(Seq(name.value))
      assert(!selection.head("fixture.ExcludedSuite"))
      assert(selection.head(name.value))
      listener.startGroup(name.value)
      listener.endGroup(name.value, TestResult.Passed)
      owner.consume(output(new SuiteResult(TestResult.Passed, 0, 0, 0, 0, 0, 0, 0)))
    }
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
