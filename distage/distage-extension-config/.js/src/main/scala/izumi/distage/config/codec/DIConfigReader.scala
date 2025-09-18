package izumi.distage.config.codec

import izumi.reflect.Tag

import scala.reflect.ClassTag
import scala.util.Try
import izumi.distage.config.DistageConfigImpl

trait DIConfigReader[A] extends AbstractDIConfigReader[A] {
  def decodeConfig(config: DistageConfigImpl): Try[A] = ???

  def decodeConfig(path: String)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    ???
  }

  def map[B](f: A => B): DIConfigReader[B] = ???

  def flatMap[B](f: A => DIConfigReader[B]): DIConfigReader[B] = ???

  def decodeConfigWithDefault(path: String)(default: => A)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    ???
  }
}

object DIConfigReader {
  @inline def apply[T: DIConfigReader]: DIConfigReader[T] = implicitly

  def derived[T: ClassTag]: DIConfigReader[T] = ???

  implicit final def todo[T: ClassTag]: DIConfigReader[T] = new DIConfigReader[T] {
    override def decodeConfig(config: DistageConfigImpl): Try[T] = ???
    override def decodeConfig(path: String)(config: DistageConfigImpl)(implicit tag: Tag[T]): T = ???
    override def decodeConfigWithDefault(path: String)(default: => T)(config: DistageConfigImpl)(implicit tag: Tag[T]): T = ???
  }
}
