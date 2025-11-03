package izumi.distage.config.codec

import izumi.distage.config.DistageConfigImpl
import izumi.reflect.Tag

import scala.util.Try

private[codec] trait AbstractDIConfigReader[A] {
  def decodeConfig(config: DistageConfigImpl): Try[A]
  def decodeConfig(path: String)(config: DistageConfigImpl)(implicit tag: Tag[A]): A
  def decodeConfigWithDefault(path: String)(default: => A)(config: DistageConfigImpl)(implicit tag: Tag[A]): A
}
