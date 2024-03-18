package izumi.distage.config.model

import com.typesafe.config.{Config, ConfigFactory}
import izumi.distage.config.DistageConfigImpl

import java.io.File

final case class AppConfig(
  config: DistageConfigImpl,
  shared: List[ConfigLoadResult.Success],
  roles: List[LoadedRoleConfigs],
) {
  // FIXME: exclude `shared` & `roles` fields from equals/hashCode for now,
  //  to prevent them breaking test environment merging for memoization
  //  (fields added in https://github.com/7mind/izumi/pull/2040)
  override def equals(obj: Any): Boolean = obj match {
    case that: AppConfig => this.config == that.config
    case _ => false
  }
  override def hashCode(): Int = config.hashCode()
}

object AppConfig {
  val empty: AppConfig = AppConfig(ConfigFactory.empty(), List.empty, List.empty)
  def provided(config: DistageConfigImpl): AppConfig = AppConfig(config, List.empty, List.empty)
}

sealed trait GenericConfigSource

object GenericConfigSource {
  case class ConfigFile(file: File) extends GenericConfigSource

  case object ConfigDefault extends GenericConfigSource
}

case class RoleConfig(role: String, active: Boolean, configSource: GenericConfigSource)

case class LoadedRoleConfigs(roleConfig: RoleConfig, loaded: Seq[ConfigLoadResult.Success])

sealed trait ConfigLoadResult {
  def clue: String

  def src: ConfigSource

  def toEither: Either[ConfigLoadResult.Failure, ConfigLoadResult.Success]
}

object ConfigLoadResult {
  case class Success(clue: String, src: ConfigSource, config: Config) extends ConfigLoadResult {
    override def toEither: Either[ConfigLoadResult.Failure, ConfigLoadResult.Success] = Right(this)
  }

  case class Failure(clue: String, src: ConfigSource, failure: Throwable) extends ConfigLoadResult {
    override def toEither: Either[ConfigLoadResult.Failure, ConfigLoadResult.Success] = Left(this)
  }
}

sealed trait ConfigSource

object ConfigSource {
  final case class Resource(name: String) extends ConfigSource {
    override def toString: String = s"resource:$name"
  }

  final case class File(file: java.io.File) extends ConfigSource {
    override def toString: String = s"file:$file"
  }
}
