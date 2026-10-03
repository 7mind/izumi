package izumi.distage.framework.services

import izumi.distage.config.DistageConfigImpl
import izumi.distage.config.model.*
import izumi.distage.model.definition.Id
import izumi.distage.model.exceptions.DIException
import izumi.functional.bio.F
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.api.IzLogger

import java.io.FileNotFoundException
import scala.util.{Failure, Success, Try}

trait ConfigLoader {
  def loadConfig(clue: String): AppConfig

  final def map(f: AppConfig => AppConfig): ConfigLoader = (clue: String) => f(loadConfig(clue))
}

object ConfigLoader {
  final class ConfigLoaderException(message: String, val failures: List[Throwable]) extends DIException(message)

  def empty: ConfigLoader = _ => AppConfig.empty

  open class LocalFSImpl(
    logger: IzLogger @Id("early"),
    merger: ConfigMerger,
    configLocation: ConfigLocationProvider,
    configArgs: ConfigLoaderArgs,
    sourceReader: ConfigSourceReader,
  ) extends ConfigLoader {
    override def loadConfig(clue: String): AppConfig = {
      val maybeLoadedRoleConfigs = configArgs.configs.map {
        roleConfig =>
          val references = configLocation.forRole(roleConfig.role).map(loadConfigSource(isExplicit = false, _))
          val loaded = roleConfig.configSource match {
            case RoleConfigSource.ConfigFile(file) =>
              Seq(loadConfigSource(isExplicit = true, ConfigSource.File(file))) ++ references
            case RoleConfigSource.ConfigDefault => references
          }
          (roleConfig, loaded)
      }
      val commonExplicit = configArgs.global.map(ConfigSource.File(_)).map(loadConfigSource(isExplicit = true, _))
      val commonReferences = configLocation.commonReferenceConfigs.map(loadConfigSource(isExplicit = false, _))
      val loaded = for {
        shared <- F[Either].traverseAccumErrorsNEList(commonExplicit.toList ++ commonReferences)(_.toEither)
        role <- F[Either].traverseAccumErrors(maybeLoadedRoleConfigs) {
          case (roleConfig, results) =>
            F[Either].traverseAccumErrorsNEList(results)(_.toEither).map(LoadedRoleConfigs(roleConfig, _))
        }
      } yield (shared, role)

      loaded match {
        case Left(errors) =>
          val failures = errors.map(f => s"Failed to load ${f.src} ${f.clue}: ${f.failure.stacktraceString}")
          logger.error(s"Cannot load configuration: ${failures.toList.niceList() -> "failures"}")
          throw new ConfigLoaderException(s"Cannot load configuration: failures=${failures.toList.niceList()}", errors.map(_.failure).toList)
        case Right((shared, role)) =>
          AppConfig(merger.addSystemProps(merger.merge(shared, role, clue)), shared, role)
      }
    }

    protected def loadConfigSource(isExplicit: Boolean, source: ConfigSource): ConfigLoadResult = {
      val loaded = Try(sourceReader.read(source)).flatMap {
        case Some(content) => ConfigSourceReader.parse(content)
        case None =>
          source match {
            case _: ConfigSource.Resource if !isExplicit => Success(DistageConfigImpl.empty)
            case _ => Failure(new FileNotFoundException(s"Couldn't find config file $source"))
          }
      }
      loaded match {
        case Success(config) => ConfigLoadResult.Success(source.toString, source, isExplicit, config)
        case Failure(error) => ConfigLoadResult.Failure(source.toString, source, isExplicit, error)
      }
    }
  }
}
