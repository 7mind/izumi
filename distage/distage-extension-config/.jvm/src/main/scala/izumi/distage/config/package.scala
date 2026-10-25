package izumi.distage

import com.typesafe.config.{Config, ConfigResolveOptions}

import scala.jdk.CollectionConverters.*
import scala.util.Try

package object config {
  type DistageConfigImpl = com.typesafe.config.Config
  object DistageConfigImpl {
    def empty: DistageConfigImpl = com.typesafe.config.ConfigFactory.empty()
    def hasPath(config: DistageConfigImpl, path: String): Boolean = config.hasPath(path)
    def withFallback(config: DistageConfigImpl, fallback: DistageConfigImpl): DistageConfigImpl = config.withFallback(fallback)
    def isResolved(config: DistageConfigImpl): Boolean = config.isResolved
    def resolve(config: DistageConfigImpl): DistageConfigImpl = config.resolve()
    def resolveAllowUnresolved(config: DistageConfigImpl): DistageConfigImpl = config.resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))
    def maybeOriginDescription(config: DistageConfigImpl): Option[String] = Some(config.origin().description())
    def getConfig(config: DistageConfigImpl, path: String): Option[Config] = Try(config.getConfig(path)).toOption
    def allKeys(config: DistageConfigImpl): collection.Set[String] = config.entrySet().asScala.map(_.getKey)
    def allValues(config: DistageConfigImpl): collection.Map[String, DistageConfigValueImpl] = config.entrySet().asScala.iterator.map(e => (e.getKey, e.getValue)).toMap
  }

  type DistageConfigValueImpl = com.typesafe.config.ConfigValue
}
