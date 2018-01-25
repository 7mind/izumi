package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.model.DIKey
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import org.bitbucket.pshirshov.izumi.distage.planning.{PlanResolver, PlanResolverDefaultImpl}
import org.scalatest.WordSpec


class BootstrapTest extends WordSpec {

  "DI Context" should {
    "support cute api calls :3" in {
      import scala.language.reflectiveCalls
      val context = new DefaultBootstrapContext() {
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
