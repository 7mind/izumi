package izumi.distage.roles.test

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.github.pshirshov.test.plugins.{StaticTestMainLogIO2, StaticTestRole}
import com.github.pshirshov.test3.plugins.Fixture3
import com.typesafe.config.ConfigFactory
import distage.config.AppConfig
import distage.plugins.{PluginBase, PluginDef}
import distage.{DIKey, Injector, Locator, LocatorRef}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.distage.framework.services.RoleAppPlanner
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.{Activation, BootstrapModule, Lifecycle}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.DebugProperties
import izumi.distage.roles.launcher.ActivationParser
import izumi.distage.roles.test.fixtures.*
import izumi.distage.roles.test.fixtures.Fixture.*
import izumi.distage.roles.test.fixtures.roles.TestRole00
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogSink
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.jdk.CollectionConverters.*

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
      (locatorRef: LocatorRef) =>
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
          ":" + AdaptedAutocloseablesCase.id,
          ":" + TestRole00.id,
        )
      )

//      assert(probe.resources.getStartedCloseables() == probe.resources.getClosedCloseables().reverse.filter(!_.isInstanceOf[LogSink]))
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
              new TestPluginCatsIO,
              new AdaptedAutocloseablesCasePlugin,
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
            ":" + AdaptedAutocloseablesCase.id,
            ":" + TestRole00.id,
          )
        )

      assert(probe.resources.getStartedCloseables() == probe.resources.getClosedCloseables().reverse.filter(!_.isInstanceOf[LogSink]))
      assert(probe.resources.getStartedCloseables() != probe.resources.getClosedCloseables())
      assert(probe.resources.getCheckedResources().toSet.size == 2)
      assert(probe.resources.getCheckedResources().toSet[Any] == Set[Any](probe.locator.get[TestResource[IO]], probe.locator.get[IntegrationResource1[IO]]))
    }

    "be able to read activations from config" in {
      TestEntrypoint
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
        TestEntrypoint
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
        TestEntrypoint
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
      } ++
        probe ++
        DefaultModule[IO]
      val roots = Set(DIKey.get[Set[TestResource[IO]]]: DIKey)
      val roleAppPlanner = new RoleAppPlanner.Impl[IO](
        options = PlanningOptions(),
        activation = Activation.empty,
        bsModule = BootstrapModule.empty,
        bootloader = Injector.bootloader[Identity](BootstrapModule.empty, Activation.empty, DefaultModule.empty, PlannerInput(definition, Activation.empty, roots)),
        logger = logger,
        parser = new ActivationParser {
          override def parseActivation(config: AppConfig): Activation = ???
        },
      )

      val plans = roleAppPlanner.makePlan(roots)
      Injector().produce(plans.runtime).use {
        Injector
          .inherit[IO](_).produce(plans.app).use {
            locator =>
              IO {
                assert(probe.resources.getStartedCloseables().size == 3)
                assert(probe.resources.getCheckedResources().size == 2)
                assert(probe.resources.getCheckedResources().toSet[Any] == Set[Any](locator.get[TestResource[IO]], locator.get[IntegrationResource1[IO]]))
              }
          }.unsafeRunSync()(IORuntime.global)
      }
    }

    "integration checks are discovered and ran from resource bindings" in {
      val probe = new XXX_TestWhiteboxProbe()

      val logger = IzLogger()
      val definition = new ResourcesPluginBase {
        make[TestResource[IO]].fromResource {
          (r: IntegrationResource1[IO]) =>
            Lifecycle.fromAutoCloseable(new IntegrationResource0(r, probe.resources))
        }
        many[TestResource[IO]]
          .ref[TestResource[IO]]
      } ++
        probe ++
        DefaultModule[IO]
      val roots = Set(DIKey.get[Set[TestResource[IO]]]: DIKey)
      val roleAppPlanner = new RoleAppPlanner.Impl[IO](
        options = PlanningOptions(),
        activation = Activation.empty,
        bsModule = BootstrapModule.empty,
        bootloader = Injector.bootloader[Identity](BootstrapModule.empty, Activation.empty, DefaultModule.empty, PlannerInput(definition, Activation.empty, roots)),
        logger = logger,
        parser = new ActivationParser {
          override def parseActivation(config: AppConfig): Activation = ???
        },
      )

      val plans = roleAppPlanner.makePlan(roots)
      Injector().produce(plans.runtime).use {
        Injector
          .inherit[IO](_).produce(plans.app).use {
            locator =>
              IO {
                assert(probe.resources.getStartedCloseables().size == 3)
                assert(probe.resources.getCheckedResources().size == 2)
                assert(probe.resources.getCheckedResources().toSet[Any] == Set[Any](locator.get[TestResource[IO]], locator.get[IntegrationResource1[IO]]))
              }
          }.unsafeRunSync()(IORuntime.global)
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
      } ++
        DefaultModule[Identity] ++
        DefaultModule[IO]
      val roots = Set(DIKey.get[Set[TestResource[Identity]]]: DIKey, DIKey.get[Set[TestResource[IO]]]: DIKey)

      val roleAppPlanner = new RoleAppPlanner.Impl[IO](
        options = PlanningOptions(),
        activation = Activation.empty,
        bsModule = BootstrapModule.empty,
        bootloader = Injector.bootloader[Identity](BootstrapModule.empty, Activation.empty, DefaultModule.empty, PlannerInput(definition, Activation.empty, roots)),
        logger = logger,
        parser = new ActivationParser {
          override def parseActivation(config: AppConfig): Activation = ???
        },
      )

      val plans = roleAppPlanner.makePlan(roots)

      Injector().produce(plans.runtime).use {
        Injector
          .inherit[IO](_).produce(plans.app).use {
            locator =>
              IO {
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
          }.unsafeRunSync()(IORuntime.global)
      }
    }

    "produce config dumps and support minimization" in {
      val version = ArtifactVersion(s"0.0.0-${UUID.randomUUID().toString}")
      withProperties(overrides ++ Map(TestPluginCatsIO.versionProperty -> version.version)) {
        TestEntrypoint.main(Array("-ll", logLevel, "-u", "axiscomponentaxis:incorrect", ":configwriter", "-t", targetPath))
      }

      val cwCfg = cfg("configwriter-full", version)
      val cwCfgMin = cfg("configwriter-minimized", version)

      assert(cwCfg.exists(), s"$cwCfg exists")
      assert(cwCfgMin.exists(), s"$cwCfgMin exists")
      assert(cwCfg.length() > cwCfgMin.length())

      val role0Cfg = cfg("testrole00-full", version)
      val role0CfgMin = cfg("testrole00-minimized", version)

      assert(role0Cfg.exists(), s"$role0Cfg exists")
      assert(role0CfgMin.exists(), s"$role0CfgMin exists")
      assert(role0Cfg.length() > role0CfgMin.length())

      val cfgContent = new String(Files.readAllBytes(role0CfgMin.toPath), UTF_8)
      val role0CfgMinParsed = ConfigFactory.parseString(cfgContent)

      assert(!role0CfgMinParsed.hasPath("unrequiredEntry"))
      assert(!role0CfgMinParsed.hasPath("logger"))
      assert(!role0CfgMinParsed.hasPath("listconf"))
      assert(!role0CfgMinParsed.hasPath("testservice.unrequiredEntry"))

      assert(role0CfgMinParsed.hasPath("integrationOnlyCfg"))
      assert(role0CfgMinParsed.hasPath("integrationOnlyCfg2"))
      assert(role0CfgMinParsed.hasPath("setElementConfig"))
      assert(role0CfgMinParsed.hasPath("testservice2"))
      assert(role0CfgMinParsed.hasPath("testservice"))

      assert(role0CfgMinParsed.getString("testservice2.strval") == "xxx")
      assert(role0CfgMinParsed.getString("testservice.overridenInt") == "555")

      // ConfigWriter DOES NOT consider system properties!
      assert(role0CfgMinParsed.getInt("testservice.systemPropInt") == 222)
      assert(role0CfgMinParsed.getList("testservice.systemPropList").unwrapped().asScala.toList == List(1, 2, 3))

      val role4Cfg = cfg("testrole04-full", version)
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

    "roles do not have access to components from MainAppModule" in {
      try {
        TestEntrypoint
          .main(
            Array(
              "-ll",
              logLevel,
              ":" + FailingRole01.id,
            )
          )
        fail("The should fail but it didn't")
      } catch {
        case err: Throwable =>
          assert(err.getMessage.contains(FailingRole01.expectedError))
      }
    }

    "roles do have access to selected components from MainAppModule" in {
      TestEntrypoint
        .main(
          Array(
            "-ll",
            logLevel,
            ":" + FailingRole02.id,
          )
        )
    }

    "Roles have access to AppShutdownInitiator" in {
      ExitLatchTestEntrypoint
        .main(
          Array()
        )
    }

    "read config in bootstrap plugins" in {
      Fixture3.TestRoleAppMain.main(Array(":fixture3"))
    }

    "LogIO2 binding is available in LauncherBIO for ZIO & MonixBIO" in {
      withProperties(
        DebugProperties.`izumi.distage.roles.activation.ignore-unknown`.name -> "true",
        DebugProperties.`izumi.distage.roles.activation.warn-unset`.name -> "false",
      ) {
        val checkTestGoodRes = getClass.getResource("/check-test-good.conf").getPath
        val customRoleConfigRes = getClass.getResource("/custom-role.conf").getPath
        new StaticTestMainLogIO2[zio.IO].main(Array("-ll", logLevel, "-c", checkTestGoodRes, ":" + StaticTestRole.id, "-c", customRoleConfigRes))
//        new StaticTestMainLogIO2[monix.bio.IO].main(Array("-ll", logLevel, "-c", checkTestGoodRes, ":" + StaticTestRole.id))
      }
    }
  }

  private def cfg(role: String, version: ArtifactVersion): File = {
    Paths.get(targetPath, s"$role-${version.version}.json").toFile
  }
}
