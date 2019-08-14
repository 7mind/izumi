package izumi.fundamentals.typesafe.config

import izumi.fundamentals.reflection.SafeType0
import com.typesafe.config.{Config, ConfigValue}

import scala.reflect.runtime.{ universe => ru }

trait RuntimeConfigReader {
  def readConfigAsCaseClass(config: Config, tpe: SafeType0[ru.type]): Any
  def codecs: Map[SafeType0[ru.type], ConfigReader[_]]
  def readValue(config: ConfigValue, tpe: SafeType0[ru.type]): Any
}
