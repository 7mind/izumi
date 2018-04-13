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
  override def toString: TypeName = s"$name"
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

  def apply(id: ServiceId): IndefiniteId = new IndefiniteId(id.pkg, id.name)

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

    def apply(parent: ServiceId, name: TypeName): DTOId = new DTOId(parent.pkg :+ parent.name, name)
    def apply(parent: IndefiniteId, name: TypeName): DTOId = new DTOId(parent.pkg :+ parent.name, name)
  }

  case class IdentifierId(pkg: Package, name: TypeName) extends ScalarId

  case class AdtId(pkg: Package, name: TypeName) extends TypeId

  object AdtId {
    def apply(parent: IndefiniteId, name: TypeName): AdtId = new AdtId(parent.pkg :+ parent.name, name)
  }

  case class AliasId(pkg: Package, name: TypeName) extends TypeId

  case class EnumId(pkg: Package, name: TypeName) extends TypeId

  // TODO: remove superclass?
  case class ServiceId(pkg: Package, name: TypeName)

}

sealed trait Builtin extends TypeId {
  def aliases: List[TypeName]

  override def name: TypeName = aliases.head

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
    override def aliases: List[TypeName] = List("bool", "boolean")
  }

  case object TString extends Primitive {
    override def aliases: List[TypeName] = List("str", "string")
  }

  case object TInt8 extends Primitive {
    override def aliases: List[TypeName] = List("i08", "byte", "int8")
  }

  case object TInt16 extends Primitive {
    override def aliases: List[TypeName] = List("i16", "short", "int16")
  }

  case object TInt32 extends Primitive {
    override def aliases: List[TypeName] = List("i32", "int", "int32")
  }

  case object TInt64 extends Primitive {
    override def aliases: List[TypeName] = List("i64", "long", "int64")
  }

  case object TFloat extends Primitive {
    override def aliases: List[TypeName] = List("flt", "float")
  }

  case object TDouble extends Primitive {
    override def aliases: List[TypeName] = List("dbl", "double")
  }

  case object TUUID extends Primitive {
    override def aliases: List[TypeName] = List("uid", "uuid")
  }

  case object TTs extends Primitive with TimeTypeId {
    override def aliases: List[TypeName] = List("tsl", "datetimel", "dtl")
  }


  case object TTsTz extends Primitive with TimeTypeId {
    override def aliases: List[TypeName] = List("tsz", "datetimez", "dtz")
  }

  case object TTime extends Primitive with TimeTypeId {
    override def aliases: List[TypeName] = List("time")
  }

  case object TDate extends Primitive with TimeTypeId {
    override def aliases: List[TypeName] = List("date")
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
    .flatMap(tpe => tpe.aliases.map(a => a -> tpe))
    .toMap
}

sealed trait Generic extends Builtin {
  def args: List[TypeId]
}

object Generic {

  trait GenericCompanion {
    def aliases: List[TypeName]

  }
  case class TList(valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(valueType)

    override def aliases: List[TypeName] = TList.aliases
  }

  object TList extends GenericCompanion {
    def aliases: List[TypeName] = List("lst", "list")
  }

  case class TSet(valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(valueType)

    override def aliases: List[TypeName] = TSet.aliases
  }

  object TSet extends GenericCompanion {
    def aliases: List[TypeName] = List("set")
  }

  case class TOption(valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(valueType)

    override def aliases: List[TypeName] = TOption.aliases
  }

  object TOption extends GenericCompanion {
    def aliases: List[TypeName] = List("opt", "option")
  }


  case class TMap(keyType: ScalarId, valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(keyType, valueType)

    override def aliases: List[TypeName] = TMap.aliases
  }

  object TMap extends GenericCompanion {
    def aliases: List[TypeName] = List("map", "dict")
  }


  final val all = Set(
    TList
    , TSet
    , TOption
    , TMap
  )
    .flatMap(tpe => tpe.aliases.map(a => a -> tpe))
    .toMap
}


case class IndefiniteGeneric(pkg: Package, name: TypeName, args: List[AbstractTypeId]) extends AbstractTypeId
