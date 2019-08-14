package com.github.pshirshov.izumi.distage.impl

import com.github.pshirshov.izumi.distage.bootstrap.BootstrapLocator
import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.planning.PlanAnalyzer
import com.github.pshirshov.izumi.distage.planning.PlanAnalyzerDefaultImpl
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
