package com.github.pshirshov.izumi.distage.roles.test

import java.nio.file.Paths
import java.util.UUID

import cats.effect._
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader.PluginConfig
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.distage.roles.test.fixtures.TestPlugin
import com.github.pshirshov.izumi.fundamentals.platform.cli.{Parameters, RoleArg}
import com.github.pshirshov.izumi.fundamentals.platform.resources.ArtifactVersion
import com.github.pshirshov.izumi.fundamentals.reflection.SourcePackageMaterializer._
import org.scalatest.WordSpec


class TestLauncherBase extends RoleAppLauncher.LauncherF[IO]() {


  protected val bootstrapConfig: BootstrapConfig = BootstrapConfig(
    PluginConfig(
      debug = false
      , packagesEnabled = Seq(s"$thisPkg.fixtures")
      , packagesDisabled = Seq.empty
    )
  )
}

object ExampleLauncher extends TestLauncherBase

object TestLauncher extends TestLauncherBase {
  override protected val hook: ApplicationShutdownStrategy[IO] = new ImmediateExitShutdownStrategy()
}


object ExampleEntrypoint extends RoleAppMain.Default(TestLauncher) {
  override protected def requiredRoles: Vector[RoleArg] = Vector(
    RoleArg("testrole00", Parameters.empty, Vector.empty),
    RoleArg("testrole01", Parameters.empty, Vector.empty),
    RoleArg("testrole02", Parameters.empty, Vector.empty),
    RoleArg("testtask00", Parameters.empty, Vector.empty),
    RoleArg("configwriter", Parameters.empty, Vector.empty),
    RoleArg("help", Parameters.empty, Vector.empty),
  )
}

object TestEntrypoint extends RoleAppMain.Silent(TestLauncher)

class RoleAppTest extends WordSpec with WithProperties {

  "Role Launcher" should {
    "produce config dumps and support minimization" in {

      val version = ArtifactVersion(s"0.0.0-${UUID.randomUUID().toString}")
      withProperties(overrides ++ Map(TestPlugin.versionProperty -> version.version)) {
        TestEntrypoint.main(Array("-ll", "critical", ":configwriter", "-t", prefix))
      }

      val cfg1 = cfg("configwriter", version)
      val cfg2 = cfg("configwriter-minimized", version)

      assert(cfg1.exists())
      assert(cfg2.exists())
      assert(cfg1.length() > cfg2.length())
    }
  }

  private val prefix = "target/configwriter"

  private val overrides = Map(
    "testservice.systemPropInt" -> "265"
    , "testservice.systemPropList.0" -> "111"
    , "testservice.systemPropList.1" -> "222"
  )

  private def cfg(role: String, version: ArtifactVersion) = {
    val justConfig = Paths.get(prefix, s"$role-${version.version}.json").toFile
    justConfig
  }
}
