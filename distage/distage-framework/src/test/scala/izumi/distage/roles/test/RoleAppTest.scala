package izumi.distage.roles.test

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.UUID

import com.typesafe.config.ConfigFactory
import distage.plugins.{PluginBase, PluginDef}
import distage.{DIKey, Injector, Locator}
import izumi.distage.effect.modules.IdentityDIEffectModule
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.PluginSource
import izumi.distage.framework.services.{IntegrationChecker, RoleAppPlanner}
import izumi.distage.model.Locator.LocatorRef
import izumi.distage.model.definition.{BootstrapModule, DIResource}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.test.fixtures.Fixture.{TestResource, IntegrationResource0, IntegrationResource1, XXX_ResourceEffectsRecorder}
import izumi.distage.roles.test.fixtures._
import izumi.distage.roles.test.fixtures.roles.TestRole00
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.logstage.api.IzLogger
import org.scalatest.wordspec.AnyWordSpec

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

  val logLevel = "crit"
  //val logLevel = "info"

  "Role Launcher" should {
    "be able to start roles" in {
      val probe = new XXX_TestWhiteboxProbe()

      new RoleAppMain.Silent(
        new TestLauncher {
          override protected def pluginSource: PluginSource = super.pluginSource overridenBy probe
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
          override protected def pluginSource: PluginSource = {
            PluginSource(Seq(
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

    "integration checks are discovered and ran from a class binding when key is not an IntegrationCheck" in {
      val probe = new XXX_TestWhiteboxProbe()

      val logger = IzLogger()
      val definition = new ResourcesPluginBase {
        make[TestResource].from[IntegrationResource0]
        many[TestResource]
          .ref[TestResource]
      } ++ IdentityDIEffectModule ++ probe
      val roleAppPlanner = new RoleAppPlanner.Impl[Identity](
        PlanningOptions(),
        BootstrapModule.empty,
        logger,
      )
      val integrationChecker = new IntegrationChecker.Impl[Identity](logger)

      val plans = roleAppPlanner.makePlan(Set(DIKey.get[Set[TestResource]]), definition)
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
      val roleAppPlanner = new RoleAppPlanner.Impl[Identity](
        PlanningOptions(),
        BootstrapModule.empty,
        logger,
      )
      val integrationChecker = new IntegrationChecker.Impl[Identity](logger)

      val plans = roleAppPlanner.makePlan(Set(DIKey.get[Set[TestResource]]), definition)
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
      val roleAppPlanner = new RoleAppPlanner.Impl[Identity](
        PlanningOptions(),
        BootstrapModule.empty,
        logger,
      )
      val integrationChecker = new IntegrationChecker.Impl[Identity](logger)

      val plans = roleAppPlanner.makePlan(Set(DIKey.get[Set[TestResource]]), definition)
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
    }
  }

  private def cfg(role: String, version: ArtifactVersion) = {
    Paths.get(prefix, s"$role-${version.version}.json").toFile
  }
}
