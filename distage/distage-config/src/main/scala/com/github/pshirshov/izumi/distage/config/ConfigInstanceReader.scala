package com.github.pshirshov.izumi.distage.config

import com.typesafe.config.ConfigValue

trait ConfigInstanceReader {
  def read(value: ConfigValue, clazz: Class[_]): Product
}
