package com.github.pshirshov.izumi.idealingua.model.common

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._


trait AbstractTypeId {
  def pkg: Package

  def name: TypeName

  override def toString: TypeName = s"{${pkg.mkString(".")}#$name :${getClass.getSimpleName}}"
}


sealed trait TypeId extends AbstractTypeId

sealed trait StructureId extends TypeId

sealed trait ScalarId extends TypeId {
  this: TypeId =>
}

sealed trait TimeTypeId {
  this: ScalarId =>
}

case class IndefiniteId(pkg: Package, name: TypeName) extends AbstractTypeId {
  def toAlias: AliasId = AliasId(pkg, name)

  def toEnum: EnumId = EnumId(pkg, name)

  def toInterface: InterfaceId = InterfaceId(pkg, name)

  def toDTO: DTOId = DTOId(pkg, name)

  def toAdtId: AdtId = AdtId(pkg, name)

  def toIdentifier: IdentifierId = IdentifierId(pkg, name)

  def toService: ServiceId = ServiceId(pkg, name)
}

object IndefiniteId {
  def apply(id: TypeId): IndefiniteId = new IndefiniteId(id.pkg, id.name)

  def parse(s: String): IndefiniteId = {
    val parts = s.split('.')
    IndefiniteId(parts.toSeq.init, parts.last)
  }
}

object TypeId {

  case class InterfaceId(pkg: Package, name: TypeName) extends StructureId

  case class DTOId(pkg: Package, name: TypeName) extends StructureId

  object DTOId {
    def apply(parent: TypeId, name: TypeName): DTOId = new DTOId(parent.pkg :+ parent.name, name)
  }

  case class IdentifierId(pkg: Package, name: TypeName) extends StructureId with ScalarId

  case class AdtId(pkg: Package, name: TypeName) extends TypeId

  case class AliasId(pkg: Package, name: TypeName) extends TypeId

  case class EnumId(pkg: Package, name: TypeName) extends TypeId

  // TODO: remove superclass?
  case class ServiceId(pkg: Package, name: TypeName) extends TypeId

}

sealed trait Builtin extends TypeId {
  override def pkg: Package = Builtin.prelude

  override def toString: TypeName = s"#$name"
}

object Builtin {
  final val prelude: Package = Seq.empty
}

trait Primitive extends Builtin with ScalarId {
}

object Primitive {

  case object TBool extends Primitive {
    override def name: TypeName = "bool"
  }

  case object TString extends Primitive {
    override def name: TypeName = "str"
  }

  case object TInt8 extends Primitive {
    override def name: TypeName = "i08"
  }

  case object TInt16 extends Primitive {
    override def name: TypeName = "i16"
  }

  case object TInt32 extends Primitive {
    override def name: TypeName = "i32"
  }

  case object TInt64 extends Primitive {
    override def name: TypeName = "i64"
  }

  case object TFloat extends Primitive {
    override def name: TypeName = "flt"
  }

  case object TDouble extends Primitive {
    override def name: TypeName = "dbl"
  }

  case object TUUID extends Primitive {
    override def name: TypeName = "uid"
  }

  case object TTs extends Primitive with TimeTypeId {
    override def name: TypeName = "tsl"
  }


  case object TTsTz extends Primitive with TimeTypeId {
    override def name: TypeName = "tsz"
  }

  case object TTime extends Primitive with TimeTypeId {
    override def name: TypeName = "time"
  }

  case object TDate extends Primitive with TimeTypeId {
    override def name: TypeName = "date"
  }


  final val mapping = Set(
    TBool
    , TString
    , TInt8
    , TInt16
    , TInt32
    , TInt64
    , TDouble
    , TFloat
    , TUUID
    , TTime
    , TDate
    , TTsTz
    , TTs
    ,
  )
    .map(tpe => tpe.name -> tpe)
    .toMap
}

sealed trait Generic extends Builtin {
  def args: List[TypeId]
}

object Generic {

  case class TList(valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(valueType)

    override def name: TypeName = "list"
  }

  case class TSet(valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(valueType)

    override def name: TypeName = "set"
  }

  case class TOption(valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(valueType)

    override def name: TypeName = "opt"
  }

  case class TMap(keyType: ScalarId, valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(keyType, valueType)

    override def name: TypeName = "map"
  }

  final val all = Set("list", "set", "map", "opt")
}


case class IndefiniteGeneric(pkg: Package, name: TypeName, args: List[AbstractTypeId]) extends AbstractTypeId
