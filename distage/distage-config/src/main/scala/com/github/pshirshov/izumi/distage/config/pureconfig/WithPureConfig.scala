package com.github.pshirshov.izumi.distage.config.pureconfig

import com.github.pshirshov.izumi.distage.config.pureconfig.WithPureConfig.R
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.typesafe.config._
import pureconfig._

abstract class WithPureConfig[T]() {
  def reader: R[T]

  def read(configValue: Config): T = {
    implicit val r: R[T] = reader
    loadConfig[T](configValue) match {
      case Right(v) =>
        v
      case Left(e) =>
        throw new DIException(s"Can't read config: $e", null)
    }
  }
}

object WithPureConfig {
  type R[X] = Derivation[ConfigReader[X]]
}

