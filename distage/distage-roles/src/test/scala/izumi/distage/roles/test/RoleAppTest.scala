package izumi.distage.roles.test

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.UUID

import izumi.distage.roles.test.fixtures.TestPlugin
import izumi.fundamentals.platform.resources.ArtifactVersion
import org.scalatest.WordSpec

class RoleAppTest extends WordSpec
  with WithProperties {
  private val prefix = "target/configwriter"

  private val overrides = Map(
    "testservice.systemPropInt" -> "265"
    , "testservice.systemPropList.0" -> "111"
    , "testservice.systemPropList.1" -> "222"
  )

  "Role Launcher" should {
    "be able to start roles" in {
      TestEntrypoint.main(Array(
        "-ll", "debug",
        ":buggy",
      ))
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
    val justConfig = Paths.get(prefix, s"$role-${version.version}.json").toFile
    justConfig
  }
}
