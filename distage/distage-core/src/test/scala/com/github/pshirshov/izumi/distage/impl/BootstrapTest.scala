package com.github.pshirshov.izumi.distage.impl

import com.github.pshirshov.izumi.distage.bootstrap.DefaultBootstrapContext
import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.planning.PlanAnalyzer
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TypedRef
import com.github.pshirshov.izumi.distage.planning.PlanAnalyzerDefaultImpl
import distage.{DIKey, Tag}
import org.scalatest.WordSpec

class BootstrapTest extends WordSpec {

  "Bootstrap Context" should {
    "contain expected definitions" in {
      import scala.language.reflectiveCalls
      val context = new DefaultBootstrapContext(DefaultBootstrapContext.noProxiesBootstrap) {
        def publicLookup[T: Tag](key: DIKey): Option[TypedRef[T]] = super.lookup(key)
      }


      val maybeRef = context.find[PlanAnalyzer]
      val ref = context.publicLookup[PlanAnalyzer](DIKey.get[PlanAnalyzer])
      val refBySuper = context.publicLookup[Any](DIKey.get[PlanAnalyzer])

      assert(maybeRef.exists(_.isInstanceOf[PlanAnalyzerDefaultImpl]))
      assert(ref.exists(_.value.isInstanceOf[PlanAnalyzerDefaultImpl]))
      assert(refBySuper.exists(_.value.isInstanceOf[PlanAnalyzerDefaultImpl]))

      assert(context.get[PlanAnalyzer].isInstanceOf[PlanAnalyzerDefaultImpl])

      intercept[MissingInstanceException] {
        context.get[PlanAnalyzer]("another.one")
      }

      val badRef = context.publicLookup[Long](DIKey.get[PlanAnalyzer])
      assert(badRef.isEmpty)

      val noRef = context.find[PlanAnalyzer]("another.one")
      assert(noRef.isEmpty)

    }
  }

}
