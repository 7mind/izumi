package izumi.fixtures.compatibility

import izumi.distage.testkit.distagesuite.generic.*
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.{EventSink, RunSession, TestCancelled, TestSuite}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext
import scala.scalajs.js
import scala.util.{Failure, Success}

object GenericJSCompatibilityMain {
  private final val ExpectedSuites = 11
  private final val ExpectedTests = 75
  private final val ExpectedIntegrationSkips = 4
  private final val ExpectedCancellations = 8

  def main(args: Array[String]): Unit = {
    val _ = args
    implicit val context: scala.concurrent.ExecutionContext = JSExecutionContext.queue
    val identity = CatalogueIdentity(BuildId("source-compatibility"), BuildTargetId("generic-js"), CatalogueId("original"))
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
    val run = Future {
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      require(catalogue.suites.size == ExpectedSuites && catalogue.tests.size == ExpectedTests, catalogue.toString)
      catalogue
    }.flatMap { catalogue =>
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      session.execute(RunId("original-generic"), request).map { outcome =>
        require(!outcome.cancelled && outcome.failures.isEmpty && outcome.results.size == ExpectedTests, outcome.toString)
        require(outcome.results.count(_.status == TestStatus.Skipped) == ExpectedIntegrationSkips, "Original unavailable integration checks must remain skipped")
        val cancelled = outcome.results.filter(_.status == TestStatus.Cancelled)
        require(cancelled.size == ExpectedCancellations && cancelled.forall(_.failure.exists(_.exceptionClass == classOf[TestCancelled].getName)), "Original skip/assume bodies must retain their cancellation failures")
        require(cancelled.forall(result => result.id.path.last.startsWith("test 5 ") || result.id.path.last.startsWith("test 6 ")), "Only the original skip/assume cases may cancel")
        require(outcome.results.forall(result => result.status == TestStatus.Cancelled || result.failure.isEmpty), "Ordinary bodies must retain no failure")
        require(outcome.results.count(_.status == TestStatus.Succeeded) == ExpectedTests - ExpectedIntegrationSkips - ExpectedCancellations, "All remaining original bodies must succeed")
        require(outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet, "Executed and discovered identities must match")
        outcome.results.foreach(result => println("ORIGINAL_GENERIC_CASE suite=" + result.id.suite.value + " path=" + result.id.path.mkString(" / ") + " status=" + result.status))
      }
    }
    run.transformWith {
      case Success(_) => session.close().map { _ =>
        println("ORIGINAL_GENERIC_JS_COMPATIBILITY_OK suites=" + ExpectedSuites + " cases=" + ExpectedTests + " skipped=" + ExpectedIntegrationSkips + " cancelled=" + ExpectedCancellations + " declarations=unchanged")
      }
      case Failure(failure) => session.close().flatMap(_ => Future.failed(failure))
    }.onComplete {
      case Success(_) => ()
      case Failure(failure) =>
        failure.printStackTrace()
        js.Dynamic.global.process.exitCode = 1
    }
  }
}
