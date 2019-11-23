package izumi.distage.roles.test

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.UUID

import distage.plugins.{PluginBase, PluginDef}
import distage.{DIKey, Locator, SafeType}
import izumi.distage.model.Locator.LocatorRef
import izumi.distage.model.definition.{BootstrapModule, BootstrapModuleDef, ModuleDef}
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.planning.PlanningHook
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.config.ContextOptions
import izumi.distage.roles.model.AppActivation
import izumi.distage.roles.services.ResourceRewriter.RewriteRules
import izumi.distage.roles.services.{PluginSource, ResourceRewriter, RoleAppPlanner}
import izumi.distage.roles.test.fixtures.Fixture.{InitCounter, Resource, Resource1, Resource2}
import izumi.distage.roles.test.fixtures._
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.logstage.api.IzLogger
import org.scalatest.WordSpec

class RoleAppTest extends WordSpec
  with WithProperties {
  private val prefix = "target/configwriter"

  private val overrides = Map(
    "testservice.systemPropInt" -> "265",
    "testservice.systemPropList.0" -> "111",
    "testservice.systemPropList.1" -> "222",
  )

  object TestEntrypoint extends RoleAppMain.Silent(new TestLauncher)

  "Role Launcher" should {
    "be able to start roles" in {
      val initCounter = new InitCounter
      var locator0: LocatorRef = null
      lazy val locator: Locator = locator0.get

      new RoleAppMain.Silent({
        new TestLauncher {
          override protected def pluginSource: PluginSource = super.pluginSource.map { l =>
            l.copy(app = Seq(l.app.merge overridenBy new ModuleDef {
              make[InitCounter].from {
                locatorRef: LocatorRef =>
                  locator0 = locatorRef
                  initCounter
              }
            }))
          }
        }
      }).main(Array(
        "-ll", "info",
        ":" + AdoptedAutocloseablesCase.id,
        ":" + TestRole00.id,
      ))

      println(initCounter.startedCloseables)
      println(initCounter.closedCloseables)
      println(initCounter.checkedResources)

      assert(initCounter.startedCloseables == initCounter.closedCloseables.reverse)
      assert(initCounter.checkedResources.toSet == Set(locator.get[Resource1], locator.get[Resource2]))
    }

    "start roles regression test" in {
      val initCounter = new InitCounter
      var locator0: LocatorRef = null
      lazy val locator: Locator = locator0.get

      new RoleAppMain.Silent({
        new TestLauncher {
          override protected def pluginSource: PluginSource = super.pluginSource.map { l =>
            l.copy(app = Seq(
              new ResourcesPluginBase().morph[PluginBase],
              new ConflictPlugin,
              new TestPlugin,
              new AdoptedAutocloseablesCasePlugin,
              new PluginDef {
                make[Resource].from[Resource1]
                many[Resource].ref[Resource]
                make[InitCounter].from {
                  locatorRef: LocatorRef =>
                    locator0 = locatorRef
                    initCounter
                }
              }))
          }
        }
      }).main(Array(
        "-ll", "info",
        ":" + AdoptedAutocloseablesCase.id,
        ":" + TestRole00.id,
      ))

      println(initCounter.startedCloseables)
      println(initCounter.closedCloseables)
      println(initCounter.checkedResources)

      assert(initCounter.startedCloseables == initCounter.closedCloseables.reverse)
      assert(initCounter.checkedResources.toSet == Set(locator.get[Resource1], locator.get[Resource2]))
    }

    "integration check regression isolated" in {
      val roleAppPlanner = new RoleAppPlanner.Impl[Identity](
        ContextOptions(),
        new BootstrapModuleDef {
          make[RewriteRules].from(RewriteRules())
          make[IzLogger].from(IzLogger())
          many[PlanningHook].add[ResourceRewriter]
        },
        AppActivation.empty,
        IzLogger()
      )

      val definition = new ResourcesPluginBase() ++ new ModuleDef {
        make[Resource].from[Resource1]
        many[Resource].ref[Resource]
        make[InitCounter]
      }

      val plans = roleAppPlanner.makePlan(Set(DIKey.get[Set[Resource]]), definition)

      println("INT:" + plans.integration.render())
      println("APP:" + plans.app.render())

      val res1CreatorIntegration = plans.integration.steps.collectFirst {
        case e: ExecutableOp.WiringOp.InstantiateClass if e.target.tpe == SafeType.get[Resource1] => e
      }
      val res1CreatorApp = plans.app.steps.collectFirst {
        case e: ExecutableOp.WiringOp.InstantiateClass if e.target.tpe == SafeType.get[Resource1] => e
      }

      assert(res1CreatorIntegration.flatMap(a => res1CreatorApp.map(a -> _)).isEmpty)
    }

    "produce config dumps and support minimization" in {
      val version = ArtifactVersion(s"0.0.0-${UUID.randomUUID().toString}")
      withProperties(overrides ++ Map(TestPlugin.versionProperty -> version.version)) {
        TestEntrypoint.main(Array(
          "-ll", "critical",
          ":configwriter", "-t", prefix
        ))
      }

      val cfg1 = cfg("configwriter", version)
      val cfg2 = cfg("configwriter-minimized", version)
      val cfg3 = cfg("testrole00-minimized", version)

      assert(cfg1.exists())
      assert(cfg2.exists())
      assert(cfg3.exists())
      assert(cfg1.length() > cfg2.length())
      assert(new String(Files.readAllBytes(cfg3.toPath), UTF_8).contains("integrationOnlyCfg"))
    }
  }

  private def cfg(role: String, version: ArtifactVersion) = {
    Paths.get(prefix, s"$role-${version.version}.json").toFile
  }
}
