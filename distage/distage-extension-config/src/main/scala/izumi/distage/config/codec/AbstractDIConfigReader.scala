package izumi.distage.config.codec

import izumi.distage.config.DistageConfigImpl
import izumi.reflect.Tag

import scala.util.Try

/** This trait exists only to work around a Scala 3 bug #24330 https://github.com/scala/scala3/issues/24330 */
private[codec] trait AbstractDIConfigReader[A] {
  def decodeConfig(config: DistageConfigImpl): Try[A]
  def decodeConfig(path: String)(config: DistageConfigImpl)(implicit tag: Tag[A]): A
  def decodeConfigWithDefault(path: String)(default: => A)(config: DistageConfigImpl)(implicit tag: Tag[A]): A
}
