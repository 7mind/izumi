package izumi.distage.impl

import izumi.distage.bootstrap.BootstrapLocator
import izumi.distage.model.exceptions.MissingInstanceException
import izumi.distage.model.planning.PlanAnalyzer
import izumi.distage.planning.PlanAnalyzerDefaultImpl
import distage.DIKey
import org.scalatest.WordSpec

class BootstrapTest extends WordSpec {

  "Bootstrap Context" should {
    "contain expected definitions" in {
      val context = new BootstrapLocator(BootstrapLocator.noProxiesBootstrap)

      val maybeRef = context.find[PlanAnalyzer]
      val ref = context.lookup[PlanAnalyzer](DIKey.get[PlanAnalyzer])
      val refBySuper = context.lookup[Any](DIKey.get[PlanAnalyzer])

      assert(maybeRef.exists(_.isInstanceOf[PlanAnalyzerDefaultImpl]))
      assert(ref.exists(_.value.isInstanceOf[PlanAnalyzerDefaultImpl]))
      assert(refBySuper.exists(_.value.isInstanceOf[PlanAnalyzerDefaultImpl]))

      assert(context.get[PlanAnalyzer].isInstanceOf[PlanAnalyzerDefaultImpl])

      intercept[MissingInstanceException] {
        context.get[PlanAnalyzer]("another.one")
      }

      val badRef = context.lookup[Long](DIKey.get[PlanAnalyzer])
      assert(badRef.isEmpty)

      val noRef = context.find[PlanAnalyzer]("another.one")
      assert(noRef.isEmpty)

    }
  }

}
