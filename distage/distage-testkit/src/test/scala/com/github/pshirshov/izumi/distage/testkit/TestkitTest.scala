package com.github.pshirshov.izumi.distage.testkit

import cats.effect.IO
import com.github.pshirshov.izumi.distage.config.annotations.ConfPathId
import com.github.pshirshov.izumi.distage.config.{ConfigInjectionOptions, ConfigProvider}
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl
import com.github.pshirshov.izumi.distage.testkit.TestkitTest.NotAddedClass
import com.github.pshirshov.izumi.distage.testkit.fixtures._
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import distage.{DIKey, ModuleBase, TagK}


abstract class TestkitTest[F[_] : TagK] extends TestkitSelftest[F] {
  "testkit" must {
    "load plugins" in dio {
      (service: TestService1, locatorRef: LocatorRef, eff: DIEffect[F]) =>
        eff.maybeSuspend {
          assert(locatorRef.get.instances.exists(_.value == service))
          assert(!locatorRef.get.instances.exists(_.value.isInstanceOf[TestService2]))
        }
    }

    "create classes in `di` arguments even if they that aren't in makeBindings" in dio {
      (notAdded: NotAddedClass, eff: DIEffect[F]) =>
        eff.maybeSuspend {
          assert(notAdded == NotAddedClass())
        }
    }

    "start and close resources and role components in correct order" in {
      var ref: LocatorRef = null

      dio {
        (_: TestService1, locatorRef: LocatorRef, eff: DIEffect[F]) =>
          eff.maybeSuspend {
            ref = locatorRef
          }
      }

      val ctx = ref.get
      assert(ctx.get[SelftestCounters].closedCloseables == Seq(ctx.get[TestService1], ctx.get[TestResource2], ctx.get[TestResource1]))
    }

    "load config" in dio {
      (service: TestService2, eff: DIEffect[F]) =>
        eff.maybeSuspend {
          assert(service.cfg1.provided == 111)
          assert(service.cfg1.overriden == 222)

          assert(service.cfg.provided == 1)
          assert(service.cfg.overriden == 3)
        }
    }

    "support non-io interface" in di {
      service: TestService1 =>
        assert(service != null)
    }

    "resolve conflicts" in di {
      service: Conflict =>
        assert(service.isInstanceOf[Conflict1])
    }
  }


  override protected def contextOptions(): ModuleProviderImpl.ContextOptions = {
    super.contextOptions().copy(configInjectionOptions = ConfigInjectionOptions.make {
      // here we may patternmatch on config value context and rewrite it
      case (ConfigProvider.ConfigImport(_: ConfPathId, _), c: TestConfig) =>
        c.copy(overriden = 3)
    })
  }


  override protected def refineBindings(roots: Set[DIKey], primaryModule: ModuleBase): ModuleBase = {
    super.refineBindings(roots, primaryModule) overridenBy new ModuleDef {
      make[TestConfig].named(ConfPathId(DIKey.get[TestService2], "<test-override>", "missing-test-section")).fromValue(TestConfig(111, 222))
    }
  }
}

class TestkitTestIO extends TestkitTest[IO]

class TestkitTestIdentity extends TestkitTest[Identity]

class TestkitTestZio extends TestkitTest[scalaz.zio.IO[Throwable, ?]]

object TestkitTest {

  case class NotAddedClass()


}
