package izumi.distage.framework

import distage.Lifecycle
import izumi.distage.config.DistageConfigImpl
import izumi.distage.config.model.*
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.framework.services.*
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.logstage.api.IzLogger
import org.scalatest.wordspec.AnyWordSpec

import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

abstract class NativeConfigLoaderContract extends AnyWordSpec {
  protected def fixture: Lifecycle[Identity, NativeConfigLoaderContract.Fixture]

  import NativeConfigLoaderContract.*

  "Native JSON configuration loader" should {
    "merge active role, global and reference inputs in priority order" in {
      fixture.use {
        f =>
          val config = loader(f, args(f), includeReferences = true).loadConfig("priority")
          assert(config.config("value").flatMap(_.asString).contains("role"))
          assert(config.config("referenceVsGlobal").flatMap(_.asString).contains("reference"))
          val nested = DistageConfigImpl.getConfig(config.config, "nested").get
          assert(nested("winner").flatMap(_.asString).contains("role"))
          assert(Set("role", "global", "reference", "common").forall(nested.contains))
          assert(!config.config.contains("inactiveOnly"))
          assert(config.shared.count(_.isExplicit) == 1)
          assert(config.roles.size == 2)
          assert(config.roles.head.loaded.count(_.isExplicit) == 1)
      }
    }

    "honor reference filtering with explicit files" in {
      fixture.use {
        f =>
          val config = loader(f, args(f), includeReferences = false).loadConfig("filter")
          val nested = DistageConfigImpl.getConfig(config.config, "nested").get
          assert(Set("role", "global").forall(nested.contains))
          assert(!nested.contains("reference") && !nested.contains("common"))
      }
    }

    "treat absent optional references as empty and reject a missing explicit file" in {
      fixture.use {
        f =>
          val config = loader(f, ConfigLoaderArgs(None, Nil), includeReferences = true).loadConfig("references")
          assert(config.shared.size == 2)
          assert(config.shared.exists(_.config.isEmpty))
          val error = intercept[ConfigLoader.ConfigLoaderException] {
            loader(f, ConfigLoaderArgs(Some(f.missing), Nil), includeReferences = true).loadConfig("missing")
          }
          assert(error.failures.size == 1)
          assert(error.failures.head.isInstanceOf[FileNotFoundException])
      }
    }

    "accumulate malformed and non-object JSON errors" in {
      fixture.use {
        f =>
          val invalid = ConfigLoaderArgs(None, List(
            RoleConfig("malformed", active = true, RoleConfigSource.ConfigFile(f.malformed)),
            RoleConfig("nonobject", active = false, RoleConfigSource.ConfigFile(f.nonObject)),
          ))
          val error = intercept[ConfigLoader.ConfigLoaderException] {
            loader(f, invalid, includeReferences = true).loadConfig("invalid")
          }
          assert(error.failures.size == 2)
          assert(error.failures.exists(_.isInstanceOf[io.circe.ParsingFailure]))
          assert(error.failures.exists(_.isInstanceOf[io.circe.DecodingFailure]))
          assert(error.getMessage.contains(f.malformed) && error.getMessage.contains(f.nonObject))
      }
    }
  }

  private def args(f: Fixture): ConfigLoaderArgs = ConfigLoaderArgs(Some(f.global), List(
    RoleConfig("active", active = true, RoleConfigSource.ConfigFile(f.role)),
    RoleConfig("inactive", active = false, RoleConfigSource.ConfigFile(f.inactive)),
  ))

  private def loader(f: Fixture, arguments: ConfigLoaderArgs, includeReferences: Boolean): ConfigLoader = {
    val filter = new ConfigFilteringStrategy.Raw(includeReferences, includeReferences, ignoreAll = false)
    val merger = new ConfigMerger.ConfigMergerImpl(IzLogger.NullLogger, enableConfigEnvOverrides = false, filter)
    val locations = new ConfigLocationProvider {
      override def forRole(roleName: String): Seq[ConfigSource] = {
        Seq(ConfigSource.Resource(s"native-framework/$roleName-reference.json"))
      }
      override def commonReferenceConfigs: Seq[ConfigSource] = Seq(
        ConfigSource.Resource("native-framework/common-reference.json"),
        ConfigSource.Resource("native-framework/missing-reference.json"),
      )
    }
    new ConfigLoader.LocalFSImpl(IzLogger.NullLogger, merger, locations, arguments, f.reader)
  }
}

object NativeConfigLoaderContract {
  final case class Fixture(reader: ConfigSourceReader, global: String, role: String, inactive: String, malformed: String, nonObject: String, missing: String)

  def files(global: String, role: String, inactive: String, malformed: String, nonObject: String): Map[ConfigSource, String] = Map(
    ConfigSource.File(global) -> """{"value":"global","referenceVsGlobal":"global","nested":{"global":1,"winner":"global"}}""",
    ConfigSource.File(role) -> """{"value":"role","nested":{"role":2,"winner":"role"}}""",
    ConfigSource.File(inactive) -> """{"inactiveOnly":true}""",
    ConfigSource.File(malformed) -> """{"value":""",
    ConfigSource.File(nonObject) -> """[1,2]""",
  )

  final class DummyReader(content: Map[ConfigSource, String]) extends ConfigSourceReader {
    override def read(source: ConfigSource): Option[String] = content.get(source)
  }
}

final class NativeConfigLoaderDummyTest extends NativeConfigLoaderContract {
  override protected def fixture: Lifecycle[Identity, NativeConfigLoaderContract.Fixture] = {
    import NativeConfigLoaderContract.*
    val global = "global.json"
    val role = "role.json"
    val inactive = "inactive.json"
    val malformed = "malformed.json"
    val nonObject = "nonobject.json"
    val resources = Map[ConfigSource, String](
      ConfigSource.Resource("native-framework/common-reference.json") -> """{"value":"common","nested":{"common":3,"winner":"common"}}""",
      ConfigSource.Resource("native-framework/active-reference.json") -> """{"value":"reference","referenceVsGlobal":"reference","nested":{"reference":4,"winner":"reference"}}""",
    )
    val reader = new DummyReader(files(global, role, inactive, malformed, nonObject) ++ resources)
    Lifecycle.pure(Fixture(reader, global, role, inactive, malformed, nonObject, "missing.json"))
  }
}

final class NativeConfigLoaderFilesystemTest extends NativeConfigLoaderContract {
  override protected def fixture: Lifecycle[Identity, NativeConfigLoaderContract.Fixture] = {
    import NativeConfigLoaderContract.*
    Lifecycle.makeSimple(Files.createTempDirectory("distage-native-config-")) {
      directory =>
        List("global.json", "role.json", "inactive.json", "malformed.json", "nonobject.json").foreach {
          name => Files.deleteIfExists(directory.resolve(name)).discard()
        }
        Files.delete(directory)
    }.map {
      directory =>
        val global = directory.resolve("global.json").toString
        val role = directory.resolve("role.json").toString
        val inactive = directory.resolve("inactive.json").toString
        val malformed = directory.resolve("malformed.json").toString
        val nonObject = directory.resolve("nonobject.json").toString
        files(global, role, inactive, malformed, nonObject).foreach {
          case (ConfigSource.File(path), content) => Files.writeString(java.nio.file.Paths.get(path), content, StandardCharsets.UTF_8).discard()
          case (source, _) => throw new IllegalStateException(s"Unexpected fixture source: $source")
        }
        Fixture(new ConfigSourceReader.LocalFSImpl(this.getClass.getClassLoader), global, role, inactive, malformed, nonObject, directory.resolve("missing.json").toString)
    }
  }
}

final class NativeResourceConfigTest extends AnyWordSpec with RoleCheckableAppPlatformSpecific {
  "Native resource configuration" should {
    "read embedded UTF-8 JSON and reject missing or malformed resources" in {
      val loader = getClass.getClassLoader
      val config = specificResourceConfigLoaderImpl(loader, "native-framework/unicode.json", "resource")
      assert(config.config("label").flatMap(_.asString).contains("héllo"))
      val missing = intercept[DIConfigReadException] {
        specificResourceConfigLoaderImpl(loader, "native-framework/missing.json", "missing")
      }
      assert(missing.getMessage.contains("file not found"))
      val invalid = intercept[DIConfigReadException] {
        specificResourceConfigLoaderImpl(loader, "native-framework/malformed.json", "malformed")
      }
      assert(invalid.getCause.isInstanceOf[io.circe.ParsingFailure])
    }
  }
}
