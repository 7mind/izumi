package izumi.distage.impl

import distage.{Activation, DIKey}
import izumi.distage.bootstrap.{BootstrapLocator, Cycles}
import izumi.distage.model.exceptions.MissingInstanceException
import izumi.distage.model.planning.PlanAnalyzer
import izumi.distage.planning.PlanAnalyzerDefaultImpl
import org.scalatest.wordspec.AnyWordSpec

class BootstrapTest extends AnyWordSpec {

  "Bootstrap Context" should {
    "contain expected definitions" in {
      val context = BootstrapLocator.bootstrap(BootstrapLocator.defaultBootstrap, Activation(Cycles -> Cycles.Byname), Nil, None)

      val maybeRef = context.find[PlanAnalyzer]
      val ref = context.lookupLocal[PlanAnalyzer](DIKey.get[PlanAnalyzer])
      val refBySuper = context.lookupLocal[Any](DIKey.get[PlanAnalyzer])

      assert(maybeRef.exists(_.isInstanceOf[PlanAnalyzerDefaultImpl]))
      assert(ref.exists(_.value.isInstanceOf[PlanAnalyzerDefaultImpl]))
      assert(refBySuper.exists(_.value.isInstanceOf[PlanAnalyzerDefaultImpl]))

      assert(context.get[PlanAnalyzer].isInstanceOf[PlanAnalyzerDefaultImpl])

      intercept[MissingInstanceException] {
        context.get[PlanAnalyzer]("another.one")
      }

      intercept[AssertionError] {
        context.lookupLocal[Long](DIKey.get[PlanAnalyzer])
      }

      val noRef = context.find[PlanAnalyzer]("another.one")
      assert(noRef.isEmpty)

    }
  }

}
