package com.github.pshirshov.izumi.distage.testkit

import cats.effect.IO
import com.github.pshirshov.izumi.distage.config.annotations.ConfPathId
import com.github.pshirshov.izumi.distage.config.{ConfigInjectionOptions, ConfigProvider}
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisValue
import com.github.pshirshov.izumi.distage.model.definition.StandardAxis.Env
import com.github.pshirshov.izumi.distage.model.definition.{AxisBase, BootstrapModuleDef, ModuleDef}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles.BootstrapConfig
import com.github.pshirshov.izumi.distage.roles.model.AppActivation
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services._
import com.github.pshirshov.izumi.distage.testkit.DistagePluginTestSupport.{CacheKey, CacheValue}
import com.github.pshirshov.izumi.distage.testkit.DistageTestRunner.DistageTest
import com.github.pshirshov.izumi.distage.testkit.TestkitTest.NotAddedClass
import com.github.pshirshov.izumi.distage.testkit.fixtures._
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import distage.{DIKey, ModuleBase, TagK}
import org.scalatest.WordSpec

class DistageTestRunnerTest extends WordSpec {
  "distage test runner" should {
    "support shared component flow" in {
      val runner = new DistageTestRunner[IO]()

      val logger = IzLogger.apply(Log.Level.Debug)("phase" -> "test")

      val env = loadEnvironment(logger)

      runner.register(DistageTest({
        service: TestService1 =>
          for {
            _ <- IO.delay(assert(service != null))
            _ <- IO.delay(println("test1"))
          } yield {

          }

      }, env))

      runner.register(DistageTest({
        service: TestService1 =>
          for {
            _ <- IO.delay(assert(service != null))
            _ <- IO.delay(println("test2"))
          } yield {

          }

      }, env))

      runner.run()

    }
  }



  /**
    * Merge strategy will be applied only once for all the tests with the same bootstrap config when memoization is on
    */
  final def loadEnvironment(logger: IzLogger): TestEnvironment = {
    val config = bootstrapConfig

    def env(): CacheValue = {
      val plugins = makePluginLoader(config).load()
      val mergeStrategy = makeMergeStrategy(logger)
      val defApp = mergeStrategy.merge(plugins.app)
      val bootstrap = mergeStrategy.merge(plugins.bootstrap)
      val availableActivations = ActivationParser.findAvailableChoices(logger, defApp)
      CacheValue(plugins, bootstrap, defApp, availableActivations)
    }

    val plugins = if (memoizePlugins) {
      DistagePluginTestSupport.Cache.getOrCompute(CacheKey(config), env())
    } else {
      env()
    }

    doLoad(logger, plugins)
  }

  protected final def doLoad(logger: IzLogger, env: CacheValue): TestEnvironment = {
    val roles = loadRoles(logger)
    val appActivation = AppActivation(env.availableActivations, activation)
    val defBs = env.bsModule overridenBy new BootstrapModuleDef {
      make[PlanMergingPolicy].from[PruningPlanMergingPolicy]
      make[AppActivation].from(appActivation)
    }
    TestEnvironment(
      defBs,
      env.appModule,
      roles,
      appActivation,
    )
  }

  protected def memoizePlugins: Boolean = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

    System.getProperty("izumi.distage.testkit.plugins.memoize")
      .asBoolean(true)
  }

  protected def loadRoles(logger: IzLogger): RolesInfo = {
    Quirks.discard(logger)
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
  }

  protected def activation: Map[AxisBase, AxisValue] = Map(Env -> Env.Test)

  protected def makeMergeStrategy(lateLogger: IzLogger): PluginMergeStrategy = {
    Quirks.discard(lateLogger)
    SimplePluginMergeStrategy
  }

  protected def bootstrapConfig: BootstrapConfig = {
    BootstrapConfig(
      PluginConfig(debug = false, pluginPackages, Seq.empty),
      pluginBootstrapPackages.map(p => PluginConfig(debug = false, p, Seq.empty)),
    )
  }

  protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource = {
    new PluginSourceImpl(bootstrapConfig)
  }

  protected final def thisPackage: Seq[String] = Seq(this.getClass.getPackage.getName)

  protected def pluginPackages: Seq[String] = thisPackage

  protected def pluginBootstrapPackages: Option[Seq[String]] = None
}



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
