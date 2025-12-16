package izumi.distage.config.model

import izumi.distage.config.DistageConfigImpl

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
  val empty: AppConfig = AppConfig(DistageConfigImpl.empty, List.empty, List.empty)
  def provided(config: DistageConfigImpl): AppConfig = AppConfig(config, List.empty, List.empty)
}

sealed trait ConfigLoadResult {
  def clue: String
  def src: ConfigSource
  def isExplicit: Boolean
  def toEither: Either[ConfigLoadResult.Failure, ConfigLoadResult.Success]
}
object ConfigLoadResult {
  final case class Success(clue: String, src: ConfigSource, isExplicit: Boolean, config: DistageConfigImpl) extends ConfigLoadResult {
    override def toEither: Either[ConfigLoadResult.Failure, ConfigLoadResult.Success] = Right(this)
  }

  final case class Failure(clue: String, src: ConfigSource, isExplicit: Boolean, failure: Throwable) extends ConfigLoadResult {
    override def toEither: Either[ConfigLoadResult.Failure, ConfigLoadResult.Success] = Left(this)
  }
}
