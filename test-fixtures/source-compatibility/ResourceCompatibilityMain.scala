package izumi.fixtures.compatibility

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.{EventSink, RunSession}
import izumi.fundamentals.platform.IzResourcesTest

import java.net.URI
import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*

object ResourceCompatibilityMain {
  private final val ExpectedTests = 7
  private final val Timeout = 30.seconds

  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Expected the fresh fixture class directory URI")
    require(classOf[IzResourcesTest].getProtectionDomain.getCodeSource.getLocation.toURI == new URI(args(0)), "The preserved original suite must load from the fresh fixture classes")
    val executor = Executors.newSingleThreadExecutor()
    val context = ExecutionContext.fromExecutorService(executor)
    val identity = CatalogueIdentity(BuildId("source-compatibility"), BuildTargetId("resources-jvm"), CatalogueId("original"))
    val session = new RunSession(identity, Vector(() => new IzResourcesTest), context, new EventSink {
      override def accept(event: ProtocolMessage.Event): Unit = {
        val _ = event
        ()
      }
    })
    try {
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      require(catalogue.tests.size == ExpectedTests, "Discovery must preserve all seven original tests")
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val outcome = Await.result(session.execute(RunId("original-resources"), request), Timeout)
      require(outcome.successful && outcome.results.size == ExpectedTests, outcome.toString)
      require(outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet, "Executed and discovered identities must match")
      require(outcome.results.forall(_.status == TestStatus.Succeeded), "Every original body must succeed")
      outcome.results.foreach(result => println("ORIGINAL_RESOURCE_CASE " + result.id.path.mkString(" / ") + " status=" + result.status))
      println("ORIGINAL_RESOURCE_COMPATIBILITY_OK cases=" + ExpectedTests + " body=unchanged data=fixture-jar")
    } finally {
      Await.result(session.close(), Timeout)
      context.shutdown()
      require(context.awaitTermination(Timeout.length, Timeout.unit), "The fixture executor must terminate")
    }
  }
}
