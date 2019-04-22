package com.github.pshirshov.izumi.distage.testkit

import cats.effect.IO
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.testkit.TestkitTest.NotAddedClass
import com.github.pshirshov.izumi.distage.testkit.fixtures.{TestResource1, TestResource2, TestService1, TestService2, TestkitSelftest}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
import distage.TagK


abstract class TestkitTest[F[_] : TagK : DIEffect] extends TestkitSelftest[F] {

  "testkit" must {
    "load plugins" in di {
      (service: TestService1, locatorRef: LocatorRef) =>
        DIEffect[F].maybeSuspend {
          assert(locatorRef.get.instances.exists(_.value == service))
          assert(!locatorRef.get.instances.exists(_.value.isInstanceOf[TestService2]))

          val expected = Set(classOf[TestService1], classOf[TestResource1], classOf[TestResource2], classOf[ConfigurableLogRouter])
          assert(locatorRef.get.get[Set[AutoCloseable]].map(_.getClass) == expected)
          assert(locatorRef.get.parent.get.get[Set[AutoCloseable]].isEmpty)
        }
    }

    "create classes in `di` arguments even if they that aren't in makeBindings" in di {
      notAdded: NotAddedClass =>
        DIEffect[F].maybeSuspend {
          assert(notAdded == NotAddedClass())
        }
    }

    //    "start and close resources and role components in correct order" in {
    //      var ref: LocatorRef = null
    //
    //      di {
    //        (_: TestService1, locatorRef: LocatorRef) =>
    //          IO {
    //            ref = locatorRef
    //          }
    //      }
    //
    //      val ctx = ref.get
    //      assert(ctx.get[InitCounter].closedCloseables == Seq(ctx.get[TestService1], ctx.get[TestResource2], ctx.get[TestResource1]))
    //      assert(ctx.get[InitCounter].startedRoleComponents == Seq(ctx.get[TestComponent1], ctx.get[TestComponent2], ctx.get[TestComponent3]))
    //      assert(ctx.get[InitCounter].closedRoleComponents == Seq(ctx.get[TestComponent3], ctx.get[TestComponent2], ctx.get[TestComponent1]))
    //    }
    //
  }

}

class TestkitTestIO extends TestkitTest[IO]

class TestkitTestIdentity extends TestkitTest[Identity]

object TestkitTest {

  case class NotAddedClass()

}
