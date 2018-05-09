package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.bootstrap.DefaultBootstrapContext
import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.planning.PlanResolver
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.planning.PlanResolverDefaultImpl
import org.scalatest.WordSpec

class BootstrapTest extends WordSpec {

  "DI Context" should {
    "support cute api calls :3" in {
      import scala.language.reflectiveCalls
      val context = new DefaultBootstrapContext(DefaultBootstrapContext.noCogenBootstrap) {
        def publicLookup[T: Tag](key: DIKey): Option[TypedRef[T]] = super.lookup(key)
      }

      val maybeResolver = context.find[PlanResolver]
      val noResolver = context.find[PlanResolver]("another.one")

      assert(maybeResolver.exists(_.isInstanceOf[PlanResolverDefaultImpl]))
      assert(noResolver.isEmpty)
      assert(context.get[PlanResolver].isInstanceOf[PlanResolverDefaultImpl])

      intercept[MissingInstanceException] {
        context.get[PlanResolver]("another.one")
      }

      val resolverRef = context.publicLookup[PlanResolver](DIKey.get[PlanResolver])
      val resolverSuperRef = context.publicLookup[Any](DIKey.get[PlanResolver])
      val badResolver = context.publicLookup[Long](DIKey.get[PlanResolver])

      assert(resolverRef.exists(_.value.isInstanceOf[PlanResolverDefaultImpl]))
      assert(resolverSuperRef.exists(_.value.isInstanceOf[PlanResolverDefaultImpl]))
      assert(badResolver.isEmpty)
    }
  }

}
