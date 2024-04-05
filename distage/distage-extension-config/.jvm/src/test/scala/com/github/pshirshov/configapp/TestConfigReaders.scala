package com.github.pshirshov.configapp

import distage.config.ConfigModuleDef
import izumi.distage.config.codec.{ConfigMetaType, DIConfigMeta}
import izumi.distage.model.PlannerInput
import pureconfig.ConfigReader

import scala.collection.immutable.ListSet

//case class MapCaseClass(mymap: mutable.LinkedHashMap[String, HostPort]) // pureconfig can read only plain `Map`...
case class MapCaseClass(mymap: Map[String, HostPort])

case class ListCaseClass(mylist: IndexedSeq[ListSet[Wrapper[HostPort]]])

case class OptionCaseClass(optInt: Option[Int], optCustomObject: Option[NestedObject])

case class BackticksCaseClass(`boo-lean`: Boolean)

case class SealedCaseClass(sealedTrait1: SealedTrait1)

case class TupleCaseClass(tuple: (Int, String, Boolean, Option[Either[Boolean, List[String]]]))

case class CustomCaseClass(
  customObject: CustomCodecObject,
  mapCustomObject: Map[String, CustomCodecObject],
  mapListCustomObject: Map[String, List[CustomCodecObject]],
)

case class PrivateCaseClass(
  private val `private-custom-field-name`: String
) {
  val customFieldName = `private-custom-field-name`
}
case class PartiallyPrivateCaseClass(
  private val `private-custom-field-name`: String,
  publicField: Boolean,
) {
  val customFieldName = `private-custom-field-name`
}

case class NestedObject(value: Int)

class CustomCodecObject(val value: Int)
object CustomCodecObject {
  def apply(value: Int) = new CustomCodecObject(value)

  implicit val pureconfigReader: ConfigReader[CustomCodecObject] = ConfigReader.fromStringOpt {
    case "eaaxacaca" => Some(new CustomCodecObject(453))
    case "a" => Some(new CustomCodecObject(45))
    case _ => Some(new CustomCodecObject(1))
  }

  implicit val diConfigMeta: DIConfigMeta[CustomCodecObject] = new DIConfigMeta[CustomCodecObject] {
    override def tpe: ConfigMetaType = {
      ConfigMetaType.TUnknown()
    }
  }
}

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
case class Service[A](conf: A)

case class AnyValClass(anyValInt: AnyValInt)

final class AnyValInt(val int: Int) extends AnyVal

object TestConfigReaders {
  final val mapDefinition = PlannerInput.everything(new ConfigModuleDef {
    make[Service[MapCaseClass]]
    makeConfig[MapCaseClass]("MapCaseClass")
  })

  final val listDefinition = PlannerInput.everything(new ConfigModuleDef {
    make[Service[ListCaseClass]]
    makeConfig[ListCaseClass]("ListCaseClass")
  })

  final val optDefinition = PlannerInput.everything(new ConfigModuleDef {
    make[Service[OptionCaseClass]]
    makeConfig[OptionCaseClass]("OptionCaseClass")
  })

  final val backticksDefinition = PlannerInput.everything(new ConfigModuleDef {
    make[Service[BackticksCaseClass]]
    makeConfig[BackticksCaseClass]("BackticksCaseClass")
  })

  final val sealedDefinition = PlannerInput.everything(new ConfigModuleDef {
    make[Service[SealedCaseClass]]
    makeConfig[SealedCaseClass]("SealedCaseClass")
  })

  final val tupleDefinition = PlannerInput.everything(new ConfigModuleDef {
    make[Service[TupleCaseClass]]
    makeConfig[TupleCaseClass]("TupleCaseClass")
  })

  final val customCodecDefinition = PlannerInput.everything(new ConfigModuleDef {
    make[Service[CustomCaseClass]]
    makeConfig[CustomCaseClass]("CustomCaseClass")
  })

  final val privateFieldsCodecDefinition = PlannerInput.everything(new ConfigModuleDef {
    make[Service[PrivateCaseClass]]
    makeConfig[PrivateCaseClass]("PrivateCaseClass")
  })

  final val partiallyPrivateFieldsCodecDefinition = PlannerInput.everything(new ConfigModuleDef {
    make[Service[PartiallyPrivateCaseClass]]
    makeConfig[PartiallyPrivateCaseClass]("PartiallyPrivateCaseClass")
  })

  // Derivation for AnyVals is not supported on Scala 3, requires a custom macro / library like
  // https://github.com/softwaremill/tapir/blob/84e4b3cb3bb209ac6c4fde3aecb13ca71afe6609/core/src/main/scala-3/sttp/tapir/internal/CodecValueClassMacro.scala
//  final val anyvalCodecDefinition = PlannerInput.everything(new ConfigModuleDef {
//    make[Service[AnyValClass]]
//    makeConfig[AnyValClass]("AnyValClass")
//  })
}
