package izumi.distage.framework

import io.circe.JsonObject
import izumi.distage.config.model.AppConfig

import scala.annotation.unused

private[framework] trait RoleCheckableAppPlatformSpecific {

  private[framework] final def specificResourceConfigLoaderImpl(@unused classLoader: ClassLoader, @unused resourceName: String, @unused clue: String): AppConfig = {
    AppConfig(JsonObject.empty, Nil, Nil)
  }

  // We can't load resources at runtime on Scala.js. We can do it at compile-time - but then we can't link the same code into JS OR easily prevent it from being linked
  // Either way, if we were to do it, we'd then support reading config from resources only for compile-time checks, but these checks
  // then couldn't be repeated at runtime, which is against the idea of being able to repeat the same checks.
//  private[framework] final def specificResourceConfigLoaderImpl(classLoader: ClassLoader, resourceName: String, @unused clue: String): AppConfig = {
//    val resourceStr = IzResources(classLoader).readAsString(resourceName).getOrElse {
//      throw new DIConfigReadException(s"Couldn't find a config resource with name `$resourceName` - file not found", null)
//    }
//    val jsonObject = io.circe.parser.parse(resourceStr).flatMap(_.as[JsonObject]).toTry.get
//    AppConfig(jsonObject, Nil, Nil)
//  }

}
