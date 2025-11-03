package izumi.distage

package object config {
  type DistageConfigImpl = io.circe.JsonObject
  object DistageConfigImpl {
    def empty: DistageConfigImpl = io.circe.JsonObject.empty
  }

  type DistageConfigValueImpl = io.circe.Json
}
