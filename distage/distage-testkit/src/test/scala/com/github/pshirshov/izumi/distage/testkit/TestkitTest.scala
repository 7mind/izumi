package com.github.pshirshov.izumi.distage.testkit

import cats.effect.IO
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.testkit.TestkitTest.NotAddedClass
import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter

object TestkitTest {
  case class NotAddedClass()
}

class TestkitTest extends DistagePluginSpec2[IO] {

  "testkit" must {
    "load plugins" in di {
      (service: TestService1, locatorRef: LocatorRef) =>
        IO {
          assert(locatorRef.get.instances.exists(_.value == service))
          assert(!locatorRef.get.instances.exists(_.value.isInstanceOf[TestService2]))

          val expected = Set(classOf[TestService1], classOf[TestResource1], classOf[TestResource2], classOf[ConfigurableLogRouter])
          assert(locatorRef.get.get[Set[AutoCloseable]].map(_.getClass) == expected)
          assert(locatorRef.get.parent.get.get[Set[AutoCloseable]].isEmpty)
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
    "create classes in `di` arguments even if they that aren't in makeBindings" in di {
      notAdded: NotAddedClass =>
        IO {
          assert(notAdded == NotAddedClass())
        }
    }
  }

  override protected def pluginPackages: Seq[String] = thisPackage

}
