package izumi.distage.framework

import izumi.distage.config.model.{AppConfig, ConfigSource}
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.framework.services.ConfigSourceReader

import scala.util.{Failure, Success}

private[framework] trait RoleCheckableAppPlatformSpecific {
  private[framework] final def specificResourceConfigLoaderImpl(classLoader: ClassLoader, resourceName: String, clue: String): AppConfig = {
    val reader = new ConfigSourceReader.LocalFSImpl(classLoader)
    val content = reader.read(ConfigSource.Resource(resourceName)).getOrElse {
      throw new DIConfigReadException(s"Couldn't find a config resource with name `$resourceName` ($clue) - file not found", null)
    }
    ConfigSourceReader.parse(content) match {
      case Success(config) => AppConfig.provided(config)
      case Failure(error) => throw new DIConfigReadException(s"Couldn't parse a JSON config resource with name `$resourceName` ($clue)", error)
    }
  }
}
