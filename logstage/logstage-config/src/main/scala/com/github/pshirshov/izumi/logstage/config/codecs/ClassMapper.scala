package com.github.pshirshov.izumi.logstage.config.codecs

import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{ConfigReader, RuntimeConfigReader, RuntimeConfigReaderCodecs, RuntimeConfigReaderDefaultImpl}
import com.typesafe.config.Config

import scala.reflect.runtime.universe
import scala.util.Try

private[logstage] abstract class ClassMapper[+T: universe.TypeTag] {

  type Params

  implicit protected def paramsTag: universe.TypeTag[Params]

  protected def apply(props: Params): T

  def path: String = universe.typeOf[T].toString

  def instantiate(config: Config): Try[T] = {
    withConfig(config).map(apply)
  }

  protected def extraCodecs : Map[SafeType0[universe.type], ConfigReader[_]] = Map.empty

  protected def reader: RuntimeConfigReader = new RuntimeConfigReaderDefaultImpl(RuntimeConfigReaderCodecs.default.readerCodecs ++ extraCodecs)

  protected def withConfig(config: Config): Try[Params] = {
    Try(reader.readConfigAsCaseClass(config, SafeType0.get[Params]).asInstanceOf[Params])
  }
}
