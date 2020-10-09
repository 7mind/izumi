package izumi.distage.roles.test

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.UUID

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import distage.plugins.{PluginBase, PluginDef}
import distage.{DIKey, Injector, Locator, LocatorRef}
import izumi.distage.framework.PlanCheck
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.services.{IntegrationChecker, RoleAppPlanner}
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.{Activation, BootstrapModule, Lifecycle}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.test.fixtures.Fixture._
import izumi.distage.roles.test.fixtures._
import izumi.distage.roles.test.fixtures.roles.TestRole00
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.logstage.api.IzLogger
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class RoleAppTest extends AnyWordSpec with WithProperties {
  private final val targetPath = "target/configwriter"

  private val overrides = Map(
    "testservice.systemPropInt" -> "265",
    "testservice.systemPropList.0" -> "111",
    "testservice.systemPropList.1" -> "222",
  )

  class XXX_TestWhiteboxProbe extends PluginDef {
    val resources = new XXX_ResourceEffectsRecorder[IO]
    private var locator0: LocatorRef = null
    lazy val locator: Locator = locator0.get

    make[XXX_ResourceEffectsRecorder[IO]].fromValue(resources)
    make[XXX_LocatorLeak].from {
      locatorRef: LocatorRef =>
        locator0 = locatorRef
        XXX_LocatorLeak(locator0)
    }
  }

//  val logLevel = "warn"
  val logLevel = "info"

  "Role Launcher" should {
    "be able to start roles" in {
      val probe = new XXX_TestWhiteboxProbe()

      new TestEntrypointBase {
        override protected def pluginConfig: PluginConfig = super.pluginConfig overriddenBy probe
      }.main(
        Array(
          "-ll",
          logLevel,
          ":" + AdoptedAutocloseablesCase.id,
          ":" + TestRole00.id,
        )
      )

      assert(probe.resources.getStartedCloseables() == probe.resources.getClosedCloseables().reverse)
      assert(
        probe.resources.getCheckedResources().toSet == Set[IntegrationCheck[IO]](probe.locator.get[IntegrationResource0[IO]], probe.locator.get[IntegrationResource1[IO]])
      )
    }

    "start roles regression test" in {
      val probe = new XXX_TestWhiteboxProbe()

      new TestEntrypointBase() {
        override protected def pluginConfig: PluginConfig = {
          PluginConfig.const(
            Seq(
              new ResourcesPluginBase {}.morph[PluginBase],
              new ConflictPlugin,
              new TestPlugin,
              new AdoptedAutocloseablesCasePlugin,
              probe,
              new PluginDef {
                make[TestResource[IO]].from[IntegrationResource0[IO]]
                many[TestResource[IO]]
                  .ref[TestResource[IO]]
              },
            )
          )
        }
      }
        .main(
          Array(
            "-ll",
            logLevel,
            ":" + AdoptedAutocloseablesCase.id,
            ":" + TestRole00.id,
          )
        )

      assert(probe.resources.getStartedCloseables() == probe.resources.getClosedCloseables().reverse)
      assert(probe.resources.getCheckedResources().toSet.size == 2)
      assert(probe.resources.getCheckedResources().toSet[Any] == Set[Any](probe.locator.get[TestResource[IO]], probe.locator.get[IntegrationResource1[IO]]))
    }

    "be able to read activations from config" in {
      new TestEntrypointBase()
        .main(
          Array(
            "-ll",
            logLevel,
            ":" + TestRole03.id,
          )
        )
    }

    "override config activations from command-line" in {
      try {
        new TestEntrypointBase()
          .main(
            Array(
              "-ll",
              logLevel,
              "-u",
              "axiscomponentaxis:incorrect",
              ":" + TestRole03.id,
            )
          )
        fail("The app is expected to fail")
      } catch {
        case err: Throwable =>
          assert(err.getMessage.contains(TestRole03.expectedError))
      }

    }

    "be able to override list configs using system properties" in {
      withProperties(
        "listconf.ints.0" -> "3",
        "listconf.ints.1" -> "2",
        "listconf.ints.2" -> "1",
      ) {
        new TestEntrypointBase()
          .main(
            Array(
              "-ll",
              logLevel,
              ":" + TestRole04.id,
            )
          )
      }
    }

    "integration checks are discovered and ran from a class binding when key is not an IntegrationCheck" in {
      val probe = new XXX_TestWhiteboxProbe()

      val logger = IzLogger()
      val definition = new ResourcesPluginBase {
        make[TestResource[IO]].from[IntegrationResource0[IO]]
        many[TestResource[IO]]
          .ref[TestResource[IO]]
      } ++ probe ++ DefaultModule[IO]
      val roots = Set(DIKey.get[Set[TestResource[IO]]]: DIKey)
      val roleAppPlanner = new RoleAppPlanner.Impl[IO](
        options = PlanningOptions(),
        activation = Activation.empty,
        bsModule = BootstrapModule.empty,
        bootloader = Injector.bootloader[Identity](PlannerInput(definition, Activation.empty, roots), BootstrapModule.empty, DefaultModule.empty),
        logger = logger,
      )
      val integrationChecker = new IntegrationChecker.Impl[IO](logger)

      val plans = roleAppPlanner.makePlan(roots)
      Injector().produce(plans.runtime).use {
        Injector.inherit(_).produce(plans.app.shared).use {
          Injector.inherit(_).produce(plans.app.side).use {
            locator =>
              integrationChecker.checkOrFail(plans.app.sideRoots1, plans.app.sideRoots2, locator).unsafeRunSync()

              assert(probe.resources.getStartedCloseables().size == 3)
              assert(probe.resources.getCheckedResources().size == 2)
              assert(probe.resources.getCheckedResources().toSet[Any] == Set[Any](locator.get[TestResource[IO]], locator.get[IntegrationResource1[IO]]))
          }
        }
      }
    }

    "integration checks are discovered and ran from resource bindings" in {
      val probe = new XXX_TestWhiteboxProbe()

      val logger = IzLogger()
      val definition = new ResourcesPluginBase {
        make[TestResource[IO]].fromResource {
          r: IntegrationResource1[IO] =>
            Lifecycle.fromAutoCloseable(new IntegrationResource0(r, probe.resources))
        }
        many[TestResource[IO]]
          .ref[TestResource[IO]]
      } ++ probe ++ DefaultModule[IO]
      val roots = Set(DIKey.get[Set[TestResource[IO]]]: DIKey)
      val roleAppPlanner = new RoleAppPlanner.Impl[IO](
        options = PlanningOptions(),
        activation = Activation.empty,
        bsModule = BootstrapModule.empty,
        bootloader = Injector.bootloader[Identity](PlannerInput(definition, Activation.empty, roots), BootstrapModule.empty, DefaultModule.empty),
        logger = logger,
      )
      val integrationChecker = new IntegrationChecker.Impl[IO](logger)

      val plans = roleAppPlanner.makePlan(roots)
      Injector().produce(plans.runtime).use {
        Injector.inherit(_).produce(plans.app.shared).use {
          Injector.inherit(_).produce(plans.app.side).use {
            locator =>
              integrationChecker.checkOrFail(plans.app.sideRoots1, plans.app.sideRoots2, locator).unsafeRunSync()

              assert(probe.resources.getStartedCloseables().size == 3)
              assert(probe.resources.getCheckedResources().size == 2)
              assert(probe.resources.getCheckedResources().toSet[Any] == Set[Any](locator.get[TestResource[IO]], locator.get[IntegrationResource1[IO]]))
          }
        }
      }
    }

    "integration checks are discovered and ran, ignoring duplicating reference bindings" in {
      val logger = IzLogger()
      val initCounter = new XXX_ResourceEffectsRecorder[IO]
      val initCounterIdentity = new XXX_ResourceEffectsRecorder[Identity]

      val definition = new ResourcesPluginBase {
        make[IntegrationResource0[Identity]]
        make[TestResource[Identity]].using[IntegrationResource0[Identity]]
        make[TestResource[Identity] with AutoCloseable].using[IntegrationResource0[Identity]]
        many[TestResource[Identity]]
          .ref[TestResource[Identity]]
          .ref[TestResource[Identity] with AutoCloseable]
        make[XXX_ResourceEffectsRecorder[IO]].fromValue(initCounter)
        make[XXX_ResourceEffectsRecorder[Identity]].fromValue(initCounterIdentity)
      } ++ DefaultModule[Identity] ++ DefaultModule[IO]
      val roots = Set(DIKey.get[Set[TestResource[Identity]]]: DIKey, DIKey.get[Set[TestResource[IO]]]: DIKey)

      val roleAppPlanner = new RoleAppPlanner.Impl[IO](
        options = PlanningOptions(),
        activation = Activation.empty,
        bsModule = BootstrapModule.empty,
        bootloader = Injector.bootloader[Identity](PlannerInput(definition, Activation.empty, roots), BootstrapModule.empty, DefaultModule.empty),
        logger = logger,
      )
      val integrationChecker = new IntegrationChecker.Impl[IO](logger)

      val plans = roleAppPlanner.makePlan(roots)

      Injector().produce(plans.runtime).use {
        Injector.inherit(_).produce(plans.app.shared).use {
          Injector.inherit(_).produce(plans.app.side).use {
            locator =>
              integrationChecker.checkOrFail(plans.app.sideRoots1, plans.app.sideRoots2, locator).unsafeRunSync()

              assert(initCounter.getStartedCloseables().size == 2)
              assert(initCounter.getCheckedResources().size == 1)
              assert(initCounter.getCheckedResources().toSet[Any] == Set[Any](locator.get[IntegrationResource1[IO]]))

              assert(initCounterIdentity.getStartedCloseables().size == 3)
              assert(initCounterIdentity.getCheckedResources().size == 2)
              assert(
                initCounterIdentity.getCheckedResources().toSet == Set[IntegrationCheck[Identity]](
                  locator.get[IntegrationResource0[Identity]],
                  locator.get[IntegrationResource1[Identity]],
                )
              )
          }
        }
      }
    }

    "produce config dumps and support minimization" in {
      val version = ArtifactVersion(s"0.0.0-${UUID.randomUUID().toString}")
      withProperties(overrides ++ Map(TestPlugin.versionProperty -> version.version)) {
        TestEntrypoint.main(Array("-ll", logLevel, ":configwriter", "-t", targetPath))
      }

      val cwCfg = cfg("configwriter", version)
      val cwCfgMin = cfg("configwriter-minimized", version)

      assert(cwCfg.exists(), s"$cwCfg exists")
      assert(cwCfgMin.exists(), s"$cwCfgMin exists")
      assert(cwCfg.length() > cwCfgMin.length())

      val role0Cfg = cfg("testrole00", version)
      val role0CfgMin = cfg("testrole00-minimized", version)

      assert(role0Cfg.exists(), s"$role0Cfg exists")
      assert(role0CfgMin.exists(), s"$role0CfgMin exists")
      assert(role0Cfg.length() > role0CfgMin.length())

      val role0CfgMinParsed = ConfigFactory.parseString(new String(Files.readAllBytes(role0CfgMin.toPath), UTF_8))

      assert(!role0CfgMinParsed.hasPath("unrequiredEntry"))
      assert(!role0CfgMinParsed.hasPath("logger"))
      assert(!role0CfgMinParsed.hasPath("listconf"))

      assert(role0CfgMinParsed.hasPath("integrationOnlyCfg"))
      assert(role0CfgMinParsed.hasPath("integrationOnlyCfg2"))
      assert(role0CfgMinParsed.hasPath("setElementConfig"))
      assert(role0CfgMinParsed.hasPath("testservice2"))
      assert(role0CfgMinParsed.hasPath("testservice"))

      assert(role0CfgMinParsed.getString("testservice2.strval") == "xxx")
      assert(role0CfgMinParsed.getString("testservice.overridenInt") == "111")
      assert(role0CfgMinParsed.getInt("testservice.systemPropInt") == 265)
      assert(role0CfgMinParsed.getList("testservice.systemPropList").unwrapped().asScala.toList == List("111", "222"))

      val role4Cfg = cfg("testrole04", version)
      val role4CfgMin = cfg("testrole04-minimized", version)

      assert(role4Cfg.exists(), s"$role4Cfg exists")
      assert(role4CfgMin.exists(), s"$role4CfgMin exists")
      assert(role4Cfg.length() > role4CfgMin.length())

      val role4CfgMinParsed = ConfigFactory.parseString(new String(Files.readAllBytes(role4CfgMin.toPath), UTF_8))

      assert(!role4CfgMinParsed.hasPath("unrequiredEntry"))
      assert(!role4CfgMinParsed.hasPath("logger"))
      assert(!role4CfgMinParsed.hasPath("integrationOnlyCfg"))
      assert(!role4CfgMinParsed.hasPath("integrationOnlyCfg2"))
      assert(!role4CfgMinParsed.hasPath("setElementConfig"))
      assert(!role4CfgMinParsed.hasPath("testservice2"))
      assert(!role4CfgMinParsed.hasPath("testservice"))

      assert(role4CfgMinParsed.hasPath("listconf"))

      assert(role0CfgMinParsed.hasPath("activation"))
      assert(role4CfgMinParsed.hasPath("activation"))
    }

    "check role app module" in {
      PlanCheck.checkRoleApp(TestEntrypoint)
    }

  }

  private def cfg(role: String, version: ArtifactVersion): File = {
    Paths.get(targetPath, s"$role-${version.version}.json").toFile
  }
}
