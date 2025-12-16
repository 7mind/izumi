package izumi.distage

import io.circe.{ACursor, Json, JsonObject}
import izumi.distage.config.codec.DIConfigReaderPlatformSpecific

package object config {
  type DistageConfigImpl = io.circe.JsonObject
  object DistageConfigImpl {
    def empty: DistageConfigImpl = {
      io.circe.JsonObject.empty
    }
    def hasPath(config: DistageConfigImpl, path: String): Boolean = {
      downPath(config, path).isDefined
    }
    def withFallback(config: DistageConfigImpl, fallback: DistageConfigImpl): DistageConfigImpl = {
      fallback.deepMerge(config)
    }
    def isResolved(config: DistageConfigImpl): Boolean = {
      true
    }
    def resolve(config: DistageConfigImpl): DistageConfigImpl = {
      config
    }
    def resolveAllowUnresolved(config: DistageConfigImpl): DistageConfigImpl = {
      config
    }
    def maybeOriginDescription(config: DistageConfigImpl): Option[String] = {
      None
    }
    def getConfig(config: DistageConfigImpl, path: String): Option[DistageConfigImpl] = {
      downPath(config, path).flatMap(_.asObject)
    }
    def allKeys(config: DistageConfigImpl): collection.Set[String] = {
      def allPrefixedKeys(prefix: Option[String], obj: JsonObject): List[String] = {
        obj.toIterable.iterator.flatMap {
          case (k, v) =>
            val prefixedKey = prefix.fold(k)(_ + "." + k)
            prefixedKey :: (v.asObject match {
              case Some(value) =>
                allPrefixedKeys(Some(prefixedKey), value)
              case None =>
                Nil
            })
        }.toList
      }
      allPrefixedKeys(None, config).toSet
    }

    private def downPath(config: DistageConfigImpl, path: String): Option[Json] = {
      val pathParts = DIConfigReaderPlatformSpecific.splitUnquotedConfigPath(path)
      pathParts.foldLeft(config.toJson.hcursor: ACursor)(_.downField(_)).focus
    }
  }

  type DistageConfigValueImpl = io.circe.Json
}
