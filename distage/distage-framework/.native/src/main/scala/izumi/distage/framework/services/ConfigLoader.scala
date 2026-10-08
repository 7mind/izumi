package izumi.distage.framework.services

import izumi.distage.config.DistageConfigImpl
import izumi.distage.config.model.*
import izumi.distage.model.definition.Id
import izumi.distage.model.exceptions.DIException
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
  ) extends ConfigLoaderBase(logger, merger, configLocation, configArgs) {
    override protected def loadConfigSource(isExplicit: Boolean, source: ConfigSource): ConfigLoadResult = {
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
