package izumi.distage.conflicts

import izumi.distage.model.planning.AxisPoint
import izumi.distage.planning.solver.SemigraphSolver
import org.scalatest.wordspec.AnyWordSpec

class ConflictsTest extends AnyWordSpec {

  import ConflictFixtures._

  "Conflict Resolver" should {
    "resolve standard samples" in {
      val cases = Seq(
        (mutators, Set(AxisPoint("test", "prod")), true, Set("app")),
        (mutators, Set.empty[AxisPoint], false, Set("app")),
        (withLoop, Set(AxisPoint("test", "prod")), true, Set("app")),
        (withLoop, Set.empty[AxisPoint], false, Set("app")),
        (complexMutators, Set.empty[AxisPoint], true, Set("app")),
      )

      val resolver = new SemigraphSolver.SemigraphSolverImpl[String, Int, Int]

      for (((f, a, good, roots), idx) <- cases.zipWithIndex) {
        val result = resolver.resolve(f, roots, a, Set.empty)
        if (good) {
          assert(result.isRight, s"positive check #$idx failed")
        } else {
          assert(result.isLeft, s"negative check #$idx failed")
        }
      }
    }
  }
}
