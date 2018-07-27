package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.bootstrap.DefaultBootstrapContext
import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.planning.PlanAnalyzer
import com.github.pshirshov.izumi.distage.planning.PlanAnalyzerDefaultImpl
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TypedRef
import distage.{DIKey, Tag}
import org.scalatest.WordSpec

class BootstrapTest extends WordSpec {

  "DI Context" should {
    "support cute api calls :3" in {
      import scala.language.reflectiveCalls
      val context = new DefaultBootstrapContext(DefaultBootstrapContext.noCogenBootstrap) {
        def publicLookup[T: Tag](key: DIKey): Option[TypedRef[T]] = super.lookup(key)
      }

      val maybeResolver = context.find[PlanAnalyzer]
      val noResolver = context.find[PlanAnalyzer]("another.one")

      assert(maybeResolver.exists(_.isInstanceOf[PlanAnalyzerDefaultImpl]))
      assert(noResolver.isEmpty)
      assert(context.get[PlanAnalyzer].isInstanceOf[PlanAnalyzerDefaultImpl])

      intercept[MissingInstanceException] {
        context.get[PlanAnalyzer]("another.one")
      }

      val resolverRef = context.publicLookup[PlanAnalyzer](DIKey.get[PlanAnalyzer])
      val resolverSuperRef = context.publicLookup[Any](DIKey.get[PlanAnalyzer])
      val badResolver = context.publicLookup[Long](DIKey.get[PlanAnalyzer])

      assert(resolverRef.exists(_.value.isInstanceOf[PlanAnalyzerDefaultImpl]))
      assert(resolverSuperRef.exists(_.value.isInstanceOf[PlanAnalyzerDefaultImpl]))
      assert(badResolver.isEmpty)
    }
  }

}
