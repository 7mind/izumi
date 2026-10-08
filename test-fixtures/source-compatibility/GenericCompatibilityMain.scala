package izumi.fixtures.compatibility

import izumi.distage.testkit.distagesuite.generic.*
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.{EventSink, RunSession, TestCancelled, TestSuite}

import java.net.URI
import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*

object GenericCompatibilityMain {
  private final val ExpectedSuites = 11
  private final val ExpectedTests = 75
  private final val ExpectedIntegrationSkips = 4
  private final val ExpectedCancellations = 8
  private final val Timeout = 60.seconds

  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Expected the fresh fixture class directory URI")
    require(classOf[DistageTestExampleBIO].getProtectionDomain.getCodeSource.getLocation.toURI == new URI(args(0)), "The preserved original declarations must load from the fresh fixture classes")
    val context = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
    val identity = CatalogueIdentity(BuildId("source-compatibility"), BuildTargetId("generic-jvm"), CatalogueId("original"))
    val factories: Vector[() => TestSuite] = Vector(
      () => new DistageTestExampleBIO,
      () => new DistageTestExampleId,
      () => new DistageTestExampleCIO,
      () => new DistageTestExampleZIO,
      () => new DistageTestExampleZIOZEnv,
      () => new ActivationTestIdentity,
      () => new ActivationTestCIO,
      () => new ActivationTestTask,
      () => new ForcedRootTestIdentity,
      () => new ForcedRootTestCIO,
      () => new ForcedRootTestTask,
    )
    val session = new RunSession(identity, factories, context, new EventSink {
      override def accept(event: ProtocolMessage.Event): Unit = {
        val _ = event
        ()
      }
    })
    try {
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      require(catalogue.suites.size == ExpectedSuites && catalogue.tests.size == ExpectedTests, catalogue.toString)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val outcome = Await.result(session.execute(RunId("original-generic"), request), Timeout)
      require(!outcome.cancelled && outcome.failures.isEmpty && outcome.results.size == ExpectedTests, outcome.toString)
      require(outcome.results.count(_.status == TestStatus.Skipped) == ExpectedIntegrationSkips, "Original unavailable integration checks must remain skipped")
      val cancelled = outcome.results.filter(_.status == TestStatus.Cancelled)
      require(cancelled.size == ExpectedCancellations && cancelled.forall(_.failure.exists(_.exceptionClass == classOf[TestCancelled].getName)), "Original skip/assume bodies must retain their cancellation failures")
      require(cancelled.forall(result => result.id.path.last.startsWith("test 5 ") || result.id.path.last.startsWith("test 6 ")), "Only the original skip/assume cases may cancel")
      require(outcome.results.forall(result => result.status == TestStatus.Cancelled || result.failure.isEmpty), "Ordinary bodies must retain no failure")
      require(outcome.results.count(_.status == TestStatus.Succeeded) == ExpectedTests - ExpectedIntegrationSkips - ExpectedCancellations, "All remaining original bodies must succeed")
      require(outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet, "Executed and discovered identities must match")
      outcome.results.foreach(result => println("ORIGINAL_GENERIC_CASE suite=" + result.id.suite.value + " path=" + result.id.path.mkString(" / ") + " status=" + result.status))
      println("ORIGINAL_GENERIC_COMPATIBILITY_OK suites=" + ExpectedSuites + " cases=" + ExpectedTests + " skipped=" + ExpectedIntegrationSkips + " cancelled=" + ExpectedCancellations + " declarations=unchanged")
    } finally {
      Await.result(session.close(), Timeout)
      context.shutdown()
      require(context.awaitTermination(Timeout.length, Timeout.unit), "The fixture executor must terminate")
    }
  }
}
