package izumi.distage.sbt

import izumi.distage.sbt.target.TaskGroups
import sbt.{TestEvent, TestResult, TestsListener}
import sbt.testing.Event

import scala.util.control.NonFatal

private[sbt] object HostFixtures {
  final case class ReceiptFixture(receipt: HostReceipt, listener: TestsListener)

  def rejected[A <: Throwable](expected: Class[A])(body: => Unit): A = {
    val observed = try { body; None } catch { case NonFatal(cause) => Some(cause) }
    require(observed.exists(expected.isInstance), "Expected " + expected.getName + ": " + observed)
    expected.cast(observed.get)
  }

  def rejects[A <: Throwable](expected: Class[A])(body: => Unit): Unit = { val _ = rejected(expected)(body); () }

  def rejects(body: => Unit): Unit = rejects(classOf[IllegalArgumentException])(body)

  def configured(owned: Set[HostSuiteName], expected: Set[HostSuiteName]): ReceiptFixture = {
    val receipt = new HostReceipt(new TaskGroups.MemoryStore)
    val listener = receipt.configure(owned)
    expected.foreach(receipt.expect)
    ReceiptFixture(receipt, listener)
  }

  def group(listener: TestsListener, suite: HostSuiteName, result: => TestResult)(events: => Seq[Event]): Unit = {
    listener.startGroup(suite.value)
    listener.testEvent(TestEvent(events))
    listener.endGroup(suite.value, result)
  }

  final class RecordedGroups(suite: HostSuiteName, result: TestResult) extends TestsListener {
    var events = Vector.empty[Event]
    var groups = 0
    override def doInit(): Unit = ()
    override def doComplete(value: TestResult): Unit = ()
    override def startGroup(value: String): Unit = { require(value == suite.value); groups += 1 }
    override def testEvent(value: TestEvent): Unit = events ++= value.detail
    override def endGroup(value: String, cause: Throwable): Unit = throw cause
    override def endGroup(value: String, valueResult: TestResult): Unit = require(value == suite.value && valueResult == result)
  }
}
