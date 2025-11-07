package izumi.distage

package object config {
  type DistageConfigImpl = com.typesafe.config.Config
  object DistageConfigImpl {
    def empty: DistageConfigImpl = com.typesafe.config.ConfigFactory.empty()
    def hasPath(config: DistageConfigImpl, path: String): Boolean = config.hasPath(path)
    def withFallback(config: DistageConfigImpl, fallback: DistageConfigImpl): DistageConfigImpl = config.withFallback(fallback)
    def resolve(config: DistageConfigImpl): DistageConfigImpl = config.resolve()
  }

  type DistageConfigValueImpl = com.typesafe.config.ConfigValue
}
