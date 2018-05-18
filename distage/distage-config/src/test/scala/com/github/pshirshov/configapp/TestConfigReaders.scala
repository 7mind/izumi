package com.github.pshirshov.configapp

import com.github.pshirshov.izumi.distage.config.annotations.AutoConf
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef

import scala.collection.immutable.ListSet
import scala.collection.mutable

case class MapCaseClass(mymap: mutable.ListMap[String, HostPort])

case class ListCaseClass(mylist: IndexedSeq[ListSet[Wrapper[HostPort]]])

case class OptionCaseClass(optInt: Option[Int])

case class Wrapper[A](wrap: A)
case class Service[A](@AutoConf conf: A)

object TestConfigReaders {
  final val mapDefinition = new ModuleDef {
    make[Service[MapCaseClass]]
  }

  final val listDefinition = new ModuleDef {
    make[Service[ListCaseClass]]
  }

  final val optDefinition = new ModuleDef {
    make[Service[OptionCaseClass]]
  }
}
