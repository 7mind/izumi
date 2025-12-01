package izumi.distage

import io.circe.ACursor

package object config {
  type DistageConfigImpl = io.circe.JsonObject
  object DistageConfigImpl {
    def empty: DistageConfigImpl = io.circe.JsonObject.empty

    def hasPath(config: DistageConfigImpl, path: String): Boolean = {
      val pathParts = path.split('.')
      pathParts.foldLeft(config.toJson.hcursor: ACursor)(_.downField(_)).focus.isDefined
    }

    def withFallback(config: DistageConfigImpl, fallback: DistageConfigImpl): DistageConfigImpl = {
      fallback.deepMerge(config)
    }

    def resolve(config: DistageConfigImpl): DistageConfigImpl = {
      config
    }
  }

  type DistageConfigValueImpl = io.circe.Json
}
