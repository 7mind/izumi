package izumi.distage

package object config {
  type DistageConfigImpl = com.typesafe.config.Config
  object DistageConfigImpl {
    def empty: DistageConfigImpl = com.typesafe.config.ConfigFactory.empty()
  }

  type DistageConfigValueImpl = com.typesafe.config.ConfigValue
}
