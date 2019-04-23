package com.github.pshirshov.izumi.distage.testkit

import cats.effect.IO
import com.github.pshirshov.izumi.distage.config.{ConfigInjectionOptions, ConfigProvider}
import com.github.pshirshov.izumi.distage.config.annotations.ConfPathId
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.testkit.TestkitTest.NotAddedClass
import com.github.pshirshov.izumi.distage.testkit.fixtures.{SelftestCounters, TestConfig, TestResource1, TestResource2, TestService1, TestService2, TestkitSelftest}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
import distage.{DIKey, ModuleBase, TagK}


abstract class TestkitTest[F[_] : TagK : DIEffect] extends TestkitSelftest[F] {


  "testkit" must {
    "load plugins" in di {
      (service: TestService1, locatorRef: LocatorRef) =>
        DIEffect[F].maybeSuspend {
          assert(locatorRef.get.instances.exists(_.value == service))
          assert(!locatorRef.get.instances.exists(_.value.isInstanceOf[TestService2]))

//          val expected = Set(classOf[TestService1], classOf[TestResource1], classOf[TestResource2], classOf[ConfigurableLogRouter])
//          assert(locatorRef.get.get[Set[AutoCloseable]].map(_.getClass) == expected)
//          assert(locatorRef.get.parent.get.get[Set[AutoCloseable]].map(_.getClass) == Set(classOf[ConfigurableLogRouter]))
        }
    }

    "create classes in `di` arguments even if they that aren't in makeBindings" in di {
      notAdded: NotAddedClass =>
        DIEffect[F].maybeSuspend {
          assert(notAdded == NotAddedClass())
        }
    }

    "start and close resources and role components in correct order" in {
      var ref: LocatorRef = null

      di {
        (_: TestService1, locatorRef: LocatorRef) =>
          DIEffect[F].maybeSuspend {
            ref = locatorRef
          }
      }

      val ctx = ref.get
      assert(ctx.get[SelftestCounters].closedCloseables == Seq(ctx.get[TestService1], ctx.get[TestResource2], ctx.get[TestResource1]))
    }

    "load config" in di {
      service: TestService2 =>
        DIEffect[F].maybeSuspend {
          assert(service.cfg1.provided == 111)
          assert(service.cfg1.overriden == 222)

          assert(service.cfg.provided == 1)
          assert(service.cfg.overriden == 3)
        }
    }
  }

  override protected def bootstrapLogLevel: Log.Level = Log.Level.Debug

  override protected val configOptions: ConfigInjectionOptions = ConfigInjectionOptions.make {
    // here we may patternmatch on config value context and rewrite it
    case (ConfigProvider.ConfigImport(_: ConfPathId, _), c: TestConfig) =>
      c.copy(overriden = 3)
  }

  override protected def refineBindings(roots: Set[DIKey], primaryModule: ModuleBase): ModuleBase = {
    super.refineBindings(roots, primaryModule) overridenBy new ModuleDef {
      make[TestConfig].named(ConfPathId(DIKey.get[TestService2], "<test-override>", "missing-test-section")).from(TestConfig(111, 222))
    }
  }
}

class TestkitTestIO extends TestkitTest[IO]

class TestkitTestIdentity extends TestkitTest[Identity]

object TestkitTest {

  case class NotAddedClass()

}
