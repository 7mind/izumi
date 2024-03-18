package izumi.distage.impl

import distage.{Activation, DIKey}
import izumi.distage.bootstrap.{BootstrapLocator, Cycles}
import izumi.distage.model.exceptions.runtime.MissingInstanceException
import izumi.distage.planning.solver.PlanSolver
import org.scalatest.wordspec.AnyWordSpec

class BootstrapTest extends AnyWordSpec {

  "Bootstrap Context" should {
    "contain expected definitions" in {
      val context = BootstrapLocator.bootstrap(BootstrapLocator.defaultBootstrap, Activation(Cycles -> Cycles.Byname), Nil, None)

      val maybeRef = context.find[PlanSolver]
      val ref = context.lookupLocal[PlanSolver](DIKey.get[PlanSolver])
      val refBySuper = context.lookupLocal[Any](DIKey.get[PlanSolver])

      assert(maybeRef.exists(_.isInstanceOf[PlanSolver.Impl]))
      assert(ref.exists(_.value.isInstanceOf[PlanSolver.Impl]))
      assert(refBySuper.exists(_.value.isInstanceOf[PlanSolver.Impl]))

      assert(context.get[PlanSolver].isInstanceOf[PlanSolver.Impl])

      intercept[MissingInstanceException] {
        context.get[PlanSolver]("another.one")
      }

      intercept[IllegalArgumentException] {
        context.lookupLocal[Long](DIKey.get[PlanSolver])
      }

      val noRef = context.find[PlanSolver]("another.one")
      assert(noRef.isEmpty)

    }
  }

}
