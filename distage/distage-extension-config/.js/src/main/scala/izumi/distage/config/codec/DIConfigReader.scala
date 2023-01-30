package izumi.distage.config.codec

import izumi.reflect.Tag

import scala.reflect.ClassTag
import scala.util.Try
import izumi.distage.config.DistageConfigImpl

trait DIConfigReader[A] extends AbstractDIConfigReader[A] {
  final def decodeConfig(config: DistageConfigImpl): Try[A] = ???

  final def decodeConfig(path: String)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    ???
  }

  final def map[B](f: A => B): DIConfigReader[B] = ???

  final def flatMap[B](f: A => DIConfigReader[B]): DIConfigReader[B] = ???

  final def decodeConfigWithDefault(path: String)(default: => A)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    ???
  }
}

object DIConfigReader {
  @inline def apply[T: DIConfigReader]: DIConfigReader[T] = ???

  def derived[T: ClassTag]: DIConfigReader[T] = ???

}
