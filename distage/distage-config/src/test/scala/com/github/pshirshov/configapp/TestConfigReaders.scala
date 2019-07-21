package com.github.pshirshov.configapp

import com.github.pshirshov.izumi.distage.config.annotations.AutoConf
import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef

import scala.collection.immutable.ListSet
import scala.collection.mutable

case class MapCaseClass(mymap: mutable.LinkedHashMap[String, HostPort])

case class ListCaseClass(mylist: IndexedSeq[ListSet[Wrapper[HostPort]]])

case class OptionCaseClass(optInt: Option[Int])

case class BackticksCaseClass(`boo-lean`: Boolean)

case class SealedCaseClass(sealedTrait1: SealedTrait1)

case class TupleCaseClass(tuple: (Int, String, Boolean, Option[Either[Boolean, List[String]]]))

sealed trait SealedTrait1
object SealedTrait {
  case class CaseClass1(int: Int, string: String, boolean: Boolean, sealedTrait2: SealedTrait2) extends SealedTrait1
  case class CaseClass2(int: Int, boolean: Boolean, sealedTrait2: SealedTrait2) extends SealedTrait1
}

sealed trait SealedTrait2
object SealedTrait2 {
  case object Yes extends SealedTrait2
  case object No extends SealedTrait2
}

case class Wrapper[A](wrap: A)
case class Service[A](@AutoConf conf: A)

object TestConfigReaders {
  final val mapDefinition = PlannerInput.noGc(new ModuleDef {
    make[Service[MapCaseClass]]
  })

  final val listDefinition = PlannerInput.noGc(new ModuleDef {
    make[Service[ListCaseClass]]
  })

  final val optDefinition = PlannerInput.noGc(new ModuleDef {
    make[Service[OptionCaseClass]]
  })

  final val backticksDefinition = PlannerInput.noGc(new ModuleDef {
    make[Service[BackticksCaseClass]]
  })

  final val sealedDefinition = PlannerInput.noGc(new ModuleDef {
    make[Service[SealedCaseClass]]
  })

  final val tupleDefinition = PlannerInput.noGc(new ModuleDef {
    make[Service[TupleCaseClass]]
  })
}
