package izumi.distage.roles.test

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.UUID

import com.typesafe.config.ConfigFactory
import distage.plugins.{PluginBase, PluginDef}
import distage.{DIKey, Injector, Locator, LocatorRef}
import izumi.distage.effect.modules.IdentityDIEffectModule
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.services.{IntegrationChecker, RoleAppPlanner}
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.{BootstrapModule, DIResource}
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.test.fixtures.Fixture._
import izumi.distage.roles.test.fixtures._
import izumi.distage.roles.test.fixtures.roles.TestRole00
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.logstage.api.IzLogger
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Try

class RoleAppTest extends AnyWordSpec
  with WithProperties {
  private val prefix = "target/configwriter"

  private val overrides = Map(
    "testservice.systemPropInt" -> "265",
    "testservice.systemPropList.0" -> "111",
    "testservice.systemPropList.1" -> "222",
  )

  object TestEntrypoint extends RoleAppMain.Silent(new TestLauncher)

  class XXX_TestWhiteboxProbe extends PluginDef {
    val resources = new XXX_ResourceEffectsRecorder
    private var locator0: LocatorRef = null
    lazy val locator: Locator = locator0.get

    make[XXX_ResourceEffectsRecorder].fromValue(resources)
    make[XXX_LocatorLeak].from {
      locatorRef: LocatorRef =>
        locator0 = locatorRef
        XXX_LocatorLeak(locator0)
    }
  }

  val logLevel = "warn"
  //val logLevel = "info"

  "Role Launcher" should {
    "be able to start roles" in {
      val probe = new XXX_TestWhiteboxProbe()

      new RoleAppMain.Silent(
        new TestLauncher {
          override protected def pluginConfig: PluginConfig = super.pluginConfig overridenBy probe
        }
      ).main(Array(
        "-ll", logLevel,
        ":" + AdoptedAutocloseablesCase.id,
        ":" + TestRole00.id,
      ))

      assert(probe.resources.getStartedCloseables() == probe.resources.getClosedCloseables().reverse)
      assert(probe.resources.getCheckedResources().toSet == Set(probe.locator.get[IntegrationResource0], probe.locator.get[IntegrationResource1]))
    }

    "start roles regression test" in {
      val probe = new XXX_TestWhiteboxProbe()

      new RoleAppMain.Silent(
        new TestLauncher {
          override protected def pluginConfig: PluginConfig = {
            PluginConfig.const(Seq(
              new ResourcesPluginBase().morph[PluginBase],
              new ConflictPlugin,
              new TestPlugin,
              new AdoptedAutocloseablesCasePlugin,
              probe,
              new PluginDef {
                make[TestResource].from[IntegrationResource0]
                many[TestResource]
                  .ref[TestResource]
              },
            ))
          }
        }
      ).main(Array(
        "-ll", logLevel,
        ":" + AdoptedAutocloseablesCase.id,
        ":" + TestRole00.id,
      ))

      assert(probe.resources.getStartedCloseables() == probe.resources.getClosedCloseables().reverse)
      assert(probe.resources.getCheckedResources().toSet.size == 2)
      assert(probe.resources.getCheckedResources().toSet == Set(probe.locator.get[TestResource], probe.locator.get[IntegrationResource1]))
    }

    "be able to read activations from config" in {
      new RoleAppMain.Silent(new TestLauncher)
        .main(Array(
        "-ll", logLevel,
        ":" + TestRole03.id,
      ))
    }

    "override config activations from command-line" in {
      val err = Try {
        new RoleAppMain.Silent(new TestLauncher)
          .main(Array(
            "-ll", logLevel,
            "-u", "axiscomponentaxis:incorrect",
            ":" + TestRole03.id,
          ))
      }.failed.get
      assert(err.getMessage.contains(TestRole03.expectedError))
    }

    "be able to override list configs using system properties" in {
      withProperties(
        "listconf.ints.0" -> "3",
        "listconf.ints.1" -> "2",
        "listconf.ints.2" -> "1",
      ) {
        new RoleAppMain.Silent(new TestLauncher)
          .main(Array(
            "-ll", logLevel,
            ":" + TestRole04.id,
          ))
      }
    }

    "integration checks are discovered and ran from a class binding when key is not an IntegrationCheck" in {
      val probe = new XXX_TestWhiteboxProbe()

      val logger = IzLogger()
      val definition = new ResourcesPluginBase {
        make[TestResource].from[IntegrationResource0]
        many[TestResource]
          .ref[TestResource]
      } ++ IdentityDIEffectModule ++ probe
      val roots = Set(DIKey.get[Set[TestResource]]: DIKey)
      val roleAppPlanner = new RoleAppPlanner.Impl[Identity](
        PlanningOptions(),
        BootstrapModule.empty,
        logger,
        Injector.bootloader(PlannerInput(definition, roots))
      )
      val integrationChecker = new IntegrationChecker.Impl[Identity](logger)

      val plans = roleAppPlanner.makePlan(roots)
      Injector().produce(plans.runtime).use {
        Injector.inherit(_).produce(plans.app.shared).use {
          Injector.inherit(_).produce(plans.app.side).use {
            locator =>
              integrationChecker.checkOrFail(plans.app.side.declaredRoots, locator)

              assert(probe.resources.getStartedCloseables().size == 3)
              assert(probe.resources.getCheckedResources().size == 2)
              assert(probe.resources.getCheckedResources().toSet == Set(locator.get[TestResource], locator.get[IntegrationResource1]))
          }
        }
      }
    }

    "integration checks are discovered and ran from resource bindings" in {
      val probe = new XXX_TestWhiteboxProbe()

      val logger = IzLogger()
      val definition = new ResourcesPluginBase {
        make[TestResource].fromResource {
          r: IntegrationResource1 =>
            DIResource.fromAutoCloseable(new IntegrationResource0(r, probe.resources))
        }
        many[TestResource]
          .ref[TestResource]
      } ++ IdentityDIEffectModule ++ probe
      val roots = Set(DIKey.get[Set[TestResource]]: DIKey)
      val roleAppPlanner = new RoleAppPlanner.Impl[Identity](
        PlanningOptions(),
        BootstrapModule.empty,
        logger,
        Injector.bootloader(PlannerInput(definition, roots))
      )
      val integrationChecker = new IntegrationChecker.Impl[Identity](logger)

      val plans = roleAppPlanner.makePlan(roots)
      Injector().produce(plans.runtime).use {
        Injector.inherit(_).produce(plans.app.shared).use {
          Injector.inherit(_).produce(plans.app.side).use {
            locator =>
              integrationChecker.checkOrFail(plans.app.side.declaredRoots, locator)

              assert(probe.resources.getStartedCloseables().size == 3)
              assert(probe.resources.getCheckedResources().size == 2)
              assert(probe.resources.getCheckedResources().toSet == Set(locator.get[TestResource], locator.get[IntegrationResource1]))
          }
        }
      }
    }

    "integration checks are discovered and ran, ignoring duplicating reference bindings" in {
      val logger = IzLogger()
      val initCounter = new XXX_ResourceEffectsRecorder
      val definition = new ResourcesPluginBase {
        make[IntegrationResource0]
        make[TestResource].using[IntegrationResource0]
        make[TestResource with AutoCloseable].using[IntegrationResource0]
        many[TestResource]
          .ref[TestResource]
          .ref[TestResource with AutoCloseable]
        make[XXX_ResourceEffectsRecorder].fromValue(initCounter)
      } ++ IdentityDIEffectModule
      val roots = Set(DIKey.get[Set[TestResource]]: DIKey)
      val roleAppPlanner = new RoleAppPlanner.Impl[Identity](
        PlanningOptions(),
        BootstrapModule.empty,
        logger,
        Injector.bootloader(PlannerInput(definition, roots))
      )
      val integrationChecker = new IntegrationChecker.Impl[Identity](logger)

      val plans = roleAppPlanner.makePlan(roots)
      Injector().produce(plans.runtime).use {
        Injector.inherit(_).produce(plans.app.shared).use {
          Injector.inherit(_).produce(plans.app.side).use {
            locator =>
              integrationChecker.checkOrFail(plans.app.side.declaredRoots, locator)

              assert(initCounter.getStartedCloseables().size == 3)
              assert(initCounter.getCheckedResources().size == 2)
              assert(initCounter.getCheckedResources().toSet == Set(locator.get[IntegrationResource0], locator.get[IntegrationResource1]))
          }
        }
      }
    }

    "produce config dumps and support minimization" in {
      val version = ArtifactVersion(s"0.0.0-${UUID.randomUUID().toString}")
      withProperties(overrides ++ Map(TestPlugin.versionProperty -> version.version)) {
        TestEntrypoint.main(Array(
          "-ll", logLevel,
          ":configwriter", "-t", prefix
        ))
      }

      val cwCfg = cfg("configwriter", version)
      val cwCfgMin = cfg("configwriter-minimized", version)

      val roleCfg = cfg("testrole00", version)
      val roleCfgMin = cfg("testrole00-minimized", version)

      assert(cwCfg.exists(), s"$cwCfg exists")
      assert(cwCfgMin.exists(), s"$cwCfgMin exists")
      assert(roleCfg.exists(), s"$roleCfg exists")
      assert(roleCfgMin.exists(), s"$roleCfgMin exists")

      assert(cwCfg.length() > cwCfgMin.length())
      assert(roleCfg.length() > roleCfgMin.length())

      val roleCfgMinStr = new String(Files.readAllBytes(roleCfgMin.toPath), UTF_8)
      val roleCfgMinRestored = ConfigFactory.parseString(roleCfgMinStr)

      assert(roleCfgMinRestored.hasPath("integrationOnlyCfg"))
      assert(roleCfgMinRestored.hasPath("integrationOnlyCfg2"))
      assert(roleCfgMinRestored.hasPath("testservice"))
      assert(roleCfgMinRestored.hasPath("testservice2"))
      assert(!roleCfgMinRestored.hasPath("unrequiredEntry"))

      assert(roleCfgMinRestored.getString("testservice2.strval") == "xxx")
      assert(roleCfgMinRestored.getString("testservice.overridenInt") == "111")
    }

  }

  private def cfg(role: String, version: ArtifactVersion): File = {
    Paths.get(prefix, s"$role-${version.version}.json").toFile
  }
}
