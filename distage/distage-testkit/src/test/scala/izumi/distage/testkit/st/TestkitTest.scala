package izumi.distage.testkit.st

import cats.effect.IO
import distage.{DIKey, ModuleBase, TagK}
import izumi.distage.config.annotations.ConfPathId
import izumi.distage.config.{ConfigInjectionOptions, ConfigProvider}
import izumi.distage.model.Locator.LocatorRef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.monadic.DIEffect
import izumi.distage.roles.config.ContextOptions
import izumi.distage.testkit.st.TestkitTest.NotAddedClass
import izumi.distage.testkit.st.fixtures._
import izumi.fundamentals.platform.functional.Identity

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

  override protected def contextOptions(): ContextOptions = {
    super.contextOptions().copy(configInjectionOptions = ConfigInjectionOptions.make {
      // here we may patternmatch on config value context and rewrite it
      case (ConfigProvider.ConfigImport(_: ConfPathId, _), c: TestConfig) =>
        c.copy(overriden = 3)
    })
  }

  override protected def refineBindings(roots: Set[DIKey], primaryModule: ModuleBase): ModuleBase = {
    super.refineBindings(roots, primaryModule) overridenBy new ModuleDef {
      make[TestConfig].named(ConfPathId(DIKey.get[TestService2], "<test-override>", "missing-test-section")).from(TestConfig(111, 222))
    }
  }
}

class TestkitTestIO extends TestkitTest[IO]

class TestkitTestIdentity extends TestkitTest[Identity]

class TestkitTestZio extends TestkitTest[zio.IO[Throwable, ?]]

object TestkitTest {

  case class NotAddedClass()

}
