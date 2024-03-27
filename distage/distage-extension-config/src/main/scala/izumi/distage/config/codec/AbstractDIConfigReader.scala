package izumi.distage.config.codec

import izumi.distage.config.DistageConfigImpl
import izumi.distage.config.model.AppConfig
import izumi.reflect.Tag

import scala.util.Try

private[codec] trait AbstractDIConfigReader[A] {
  def decodeConfig(config: DistageConfigImpl): Try[A]

  def decodeConfig(path: String)(config: DistageConfigImpl)(implicit tag: Tag[A]): A

  def decodeConfigWithDefault(path: String)(default: => A)(config: DistageConfigImpl)(implicit tag: Tag[A]): A

  def map[B](f: A => B): DIConfigReader[B]

  def flatMap[B](f: A => DIConfigReader[B]): DIConfigReader[B]

  final def decodeAppConfig(path: String)(implicit tag: Tag[A]): AppConfig => A = {
    appConfig => decodeConfig(path)(appConfig.config)
  }

  final def decodeAppConfigWithDefault(path: String)(default: => A)(implicit tag: Tag[A]): AppConfig => A = {
    appConfig => decodeConfigWithDefault(path)(default)(appConfig.config)
  }
}
