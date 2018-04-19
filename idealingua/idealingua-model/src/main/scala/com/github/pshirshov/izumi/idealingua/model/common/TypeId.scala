package com.github.pshirshov.izumi.idealingua.model.common


sealed trait AbstractIndefiniteId {
  def pkg: Package
  def name: TypeName
}


case class IndefiniteId(pkg: Package, name: TypeName) extends AbstractIndefiniteId

object IndefiniteId {
  def parse(s: String): IndefiniteId = {
    val parts = s.split('.')
    IndefiniteId(parts.toSeq.init, parts.last)
  }
}

case class IndefiniteGeneric(pkg: Package, name: TypeName, args: List[AbstractIndefiniteId]) extends AbstractIndefiniteId


sealed trait TypeId {
  def path: TypePath
  def name: TypeName

  override def toString: TypeName = s"${getClass.getSimpleName}:$path#$name"

}

sealed trait StructureId extends TypeId

sealed trait ScalarId extends TypeId {
  this: TypeId =>
  override def toString: TypeName = s"$name"
}

sealed trait TimeTypeId {
  this: ScalarId =>
}


object TypeId {

  case class InterfaceId(path: TypePath, name: TypeName) extends StructureId

  case class DTOId(path: TypePath, name: TypeName) extends StructureId

  object DTOId {
    def apply(parent: TypeId, name: TypeName): DTOId = new DTOId(parent.path.sub(parent.name), name)

    def apply(parent: ServiceId, name: TypeName): DTOId = new DTOId(TypePath(parent.domain, Seq(parent.name)), name)
  }

  case class IdentifierId(path: TypePath, name: TypeName) extends ScalarId

  case class AdtId(path: TypePath, name: TypeName) extends TypeId

  object AdtId {
    def apply(parent: ServiceId, name: TypeName): AdtId = new AdtId(TypePath(parent.domain, Seq(parent.name)), name)
  }

  case class AliasId(path: TypePath, name: TypeName) extends TypeId

  case class EnumId(path: TypePath, name: TypeName) extends TypeId

  // TODO: remove superclass?
  case class ServiceId(domain: DomainId, name: TypeName)

}

sealed trait Builtin extends TypeId {
  def aliases: List[TypeName]

  override def name: TypeName = aliases.head


  override def path: TypePath = TypePath(DomainId.Builtin, Seq.empty)

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


