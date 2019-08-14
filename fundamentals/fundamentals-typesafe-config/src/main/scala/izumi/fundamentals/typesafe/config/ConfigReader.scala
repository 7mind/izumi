package izumi.fundamentals.typesafe.config

import com.typesafe.config.ConfigValueType._
import com.typesafe.config._

import scala.reflect.ClassTag
import scala.util.Try

// copypasta from pureconfig.BasicReaders

trait ConfigReader[T] {
  def apply(configValue: ConfigValue): Try[T]
}

object ConfigReader {
  def fromString[T: ClassTag](f: String => T): ConfigReader[T] = {
    case cv: ConfigValue if Set(STRING, BOOLEAN, NUMBER) contains cv.valueType  =>
      Try(f(String.valueOf(cv.unwrapped)))
    case cv =>
      scala.util.Failure(new ConfigReadException(
        s"""Encountered a non-String, Boolean or Number value when trying to deserialize as ${implicitly[ClassTag[T]]}
           | using a String codec. ConfigValue was: $cv""".stripMargin))
  }
}


