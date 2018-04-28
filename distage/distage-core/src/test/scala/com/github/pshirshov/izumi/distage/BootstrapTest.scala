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
      val context = new DefaultBootstrapContext(DefaultBootstrapContext.defaultBootstrapContextDefinition) {
        def publicLookup[T: Tag](key: DIKey): Option[TypedRef[T]] = super.lookup(key)
      }

      assert(context.find[PlanResolver].exists(_.isInstanceOf[PlanResolverDefaultImpl]))
      assert(context.find[PlanResolver]("another.one").isEmpty)

      assert(context.get[PlanResolver].isInstanceOf[PlanResolverDefaultImpl])
      intercept[MissingInstanceException] {
        context.get[PlanResolver]("another.one")
      }

      assert(context.publicLookup[PlanResolver](DIKey.get[PlanResolver]).exists(_.value.isInstanceOf[PlanResolverDefaultImpl]))
      assert(context.publicLookup[Any](DIKey.get[PlanResolver]).exists(_.value.isInstanceOf[PlanResolverDefaultImpl]))
      assert(context.publicLookup[Long](DIKey.get[PlanResolver]).isEmpty)

    }
  }

}
