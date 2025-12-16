package izumi.distage.framework

import com.typesafe.config.ConfigFactory
import izumi.distage.config.model.AppConfig
import izumi.distage.config.model.exceptions.DIConfigReadException

import scala.annotation.unused

private[framework] trait RoleCheckableAppPlatformSpecific {

  private[framework] final def specificResourceConfigLoaderImpl(classLoader: ClassLoader, resourceName: String, @unused clue: String): AppConfig = {
    val cfg = ConfigFactory.parseResources(classLoader, resourceName).resolve()
    if (cfg.origin().resource() eq null) {
      throw new DIConfigReadException(s"Couldn't find a config resource with name `$resourceName` - file not found", null)
    }
    AppConfig(cfg, Nil, Nil)
  }

}
