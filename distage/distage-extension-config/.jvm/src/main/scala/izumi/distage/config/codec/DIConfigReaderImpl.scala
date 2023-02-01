package izumi.distage.config.codec

import pureconfig.ConfigReader
import pureconfig.error.ConfigReaderException

import scala.reflect.ClassTag
import scala.util.{Failure, Success}

object DIConfigReaderImpl extends LowPriorityDIConfigReaderInstances2 {}

sealed trait LowPriorityDIConfigReaderInstances {
  implicit final def deriveFromPureconfigAutoDerive[T: ClassTag](implicit dec: PureconfigAutoDerive[T]): DIConfigReader[T] = {
    DIConfigReaderImpl.deriveFromPureconfigConfigReader(implicitly, dec.value)
  }
}

protected[codec] trait LowPriorityDIConfigReaderInstances2 extends LowPriorityDIConfigReaderInstances {
  def derived[T: ClassTag](implicit dec: PureconfigAutoDerive[T]): DIConfigReader[T] =
    DIConfigReaderImpl.deriveFromPureconfigConfigReader[T](implicitly, dec.value)

  implicit def deriveFromPureconfigConfigReader[T: ClassTag](implicit dec: ConfigReader[T]): DIConfigReader[T] = {
    cv =>
      dec.from(cv) match {
        case Left(errs) => Failure(ConfigReaderException[T](errs))
        case Right(value) => Success(value)
      }
  }

}
