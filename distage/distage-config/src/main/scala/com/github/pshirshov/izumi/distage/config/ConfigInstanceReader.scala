package com.github.pshirshov.izumi.distage.config

import com.typesafe.config.Config

trait ConfigInstanceReader {
  def read(value: Config, clazz: Class[_]): Product
}
