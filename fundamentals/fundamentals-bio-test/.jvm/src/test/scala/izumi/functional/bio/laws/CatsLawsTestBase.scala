package izumi.functional.bio.laws

import izumi.distage.testkit.runner.spec.AnyWordSpec
import org.scalacheck.{Prop, Test}
import org.scalacheck.util.Pretty
import org.typelevel.discipline.Laws

import scala.concurrent.Future

trait CatsLawsTestBase extends AnyWordSpec {
  private final val MinimumSuccessfulCases = 10
  private val parameters = Test.Parameters.default.withMinSuccessfulTests(MinimumSuccessfulCases)
  private var previous = Future.successful(())
  final def checkAll(name: String, ruleSet: Laws#RuleSet): Unit = {
    ruleSet.all.properties.foreach {
      case (id, property) =>
        s"$name.$id" in checkProperty(property)
    }
  }

  // Serialize the shared virtual-clock context without parking execution workers; each property retains its own outcome.
  private def checkProperty(property: Prop): Future[Unit] = synchronized {
    implicit val ec: scala.concurrent.ExecutionContext = sessionExecutionContext
    val current = previous.transformWith { _ => Future {
      val result = Test.check(parameters, property)
      assert(result.passed, Pretty.pretty(result))
    } }
    previous = current
    current
  }
}
