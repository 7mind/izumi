package com.github.pshirshov.izumi.idealingua.model.common


sealed trait AbstractIndefiniteId {
  def pkg: Package

  def name: TypeName

  override def toString: TypeName = s"{${getClass.getSimpleName}}${pkg.mkString(".")}#$name"
}


final case class IndefiniteId(pkg: Package, name: TypeName) extends AbstractIndefiniteId

final case class IndefiniteMixin(pkg: Package, name: TypeName) extends AbstractIndefiniteId

object IndefiniteId {
  def parse(s: String): IndefiniteId = {
    val parts = s.split('.')
    IndefiniteId(parts.toSeq.init, parts.last)
  }
}

final case class IndefiniteGeneric(pkg: Package, name: TypeName, args: List[AbstractIndefiniteId]) extends AbstractIndefiniteId


sealed trait TypeId {
  def path: TypePath

  def name: TypeName

  def uniqueDomainName: String = path.within.mkString("") ++ name.capitalize

  def wireId: String = {
    s"${path.toPackage.mkString(".")}.$name"
  }

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

  final case class InterfaceId(path: TypePath, name: TypeName) extends StructureId

  object InterfaceId {
    def apply(parent: TypeId, name: TypeName): InterfaceId = new InterfaceId(parent.path.sub(parent.name), name)
  }

  final case class DTOId(path: TypePath, name: TypeName) extends StructureId

  object DTOId {
    def apply(parent: TypeId, name: TypeName): DTOId = new DTOId(parent.path.sub(parent.name), name)

    def apply(parent: ServiceId, name: TypeName): DTOId = new DTOId(TypePath(parent.domain, Seq(parent.name)), name)

    def apply(parent: BuzzerId, name: TypeName): DTOId = new DTOId(TypePath(parent.domain, Seq(parent.name)), name)
  }

  final case class IdentifierId(path: TypePath, name: TypeName) extends ScalarId

  final case class AdtId(path: TypePath, name: TypeName) extends TypeId

  object AdtId {
    def apply(parent: ServiceId, name: TypeName): AdtId = new AdtId(TypePath(parent.domain, Seq(parent.name)), name)
  }

  final case class AliasId(path: TypePath, name: TypeName) extends TypeId

  final case class EnumId(path: TypePath, name: TypeName) extends ScalarId


  final case class ServiceId(domain: DomainId, name: TypeName)

  final case class BuzzerId(domain: DomainId, name: TypeName)

  final case class StreamsId(domain: DomainId, name: TypeName)

  final case class ConstId(domain: DomainId, name: TypeName)

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

sealed trait Primitive extends Builtin with ScalarId {

}

sealed trait PrimitiveId extends Primitive {

}

object Primitive {

  case object TBool extends PrimitiveId {
    override def aliases: List[TypeName] = List("bit", "bool", "boolean")
  }

  case object TString extends PrimitiveId {
    override def aliases: List[TypeName] = List("str", "string")
  }

  case object TInt8 extends PrimitiveId {
    override def aliases: List[TypeName] = List("i08", "byte", "int8")
  }

  case object TInt16 extends PrimitiveId {
    override def aliases: List[TypeName] = List("i16", "short", "int16")
  }

  case object TInt32 extends PrimitiveId {
    override def aliases: List[TypeName] = List("i32", "int", "int32")
  }

  case object TInt64 extends PrimitiveId {
    override def aliases: List[TypeName] = List("i64", "long", "int64")
  }

  case object TUInt8 extends PrimitiveId {
    override def aliases: List[TypeName] = List("u08", "ubyte", "uint8")
  }

  case object TUInt16 extends PrimitiveId {
    override def aliases: List[TypeName] = List("u16", "ushort", "uint16")
  }

  case object TUInt32 extends PrimitiveId {
    override def aliases: List[TypeName] = List("u32", "uint", "uint32")
  }

  case object TUInt64 extends PrimitiveId {
    override def aliases: List[TypeName] = List("u64", "ulong", "uint64")
  }


  case object TFloat extends Primitive {
    override def aliases: List[TypeName] = List("f32", "flt", "float")
  }

  case object TDouble extends Primitive {
    override def aliases: List[TypeName] = List("f64", "dbl", "double")
  }

  case object TUUID extends PrimitiveId {
    override def aliases: List[TypeName] = List("uid", "uuid")
  }

  /**
    * Date and Time, no timezone data
    */
  case object TTs extends Primitive with TimeTypeId {
    override def aliases: List[TypeName] = List("tsl", "datetimel", "dtl")
  }

  /**
    * Date, Time and Timezone
    */
  case object TTsTz extends Primitive with TimeTypeId {
    override def aliases: List[TypeName] = List("tsz", "datetimez", "dtz")
  }

  /**
    * Date, Time and Timezone, but Timezone is always set to UTC
    */
  case object TTsU extends Primitive with TimeTypeId {
    override def aliases: List[TypeName] = List("tsu", "datetimeu", "dtu")
  }

  /**
    * Just time, without timezone data
    */
  case object TTime extends Primitive with TimeTypeId {
    override def aliases: List[TypeName] = List("time")
  }

  /**
    * Just date, without timezone data
    */
  case object TDate extends Primitive with TimeTypeId {
    override def aliases: List[TypeName] = List("date")
  }

  final val mappingId = Set(
    TBool
    , TString

    , TInt8
    , TInt16
    , TInt32
    , TInt64

    , TUInt8
    , TUInt16
    , TUInt32
    , TUInt64


    , TUUID
    ,
  )
    .flatMap(tpe => tpe.aliases.map(a => a -> tpe))
    .toMap

  final val mapping = Set(
    TBool
    , TString
    , TInt8
    , TInt16
    , TInt32
    , TInt64
    , TUInt8
    , TUInt16
    , TUInt32
    , TUInt64
    , TDouble
    , TFloat
    , TUUID
    , TTime
    , TDate
    , TTsTz
    , TTsU
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

  final case class TList(valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(valueType)

    override def aliases: List[TypeName] = TList.aliases
  }

  object TList extends GenericCompanion {
    def aliases: List[TypeName] = List("lst", "list")
  }

  final case class TSet(valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(valueType)

    override def aliases: List[TypeName] = TSet.aliases
  }

  object TSet extends GenericCompanion {
    def aliases: List[TypeName] = List("set")
  }

  final case class TOption(valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(valueType)

    override def aliases: List[TypeName] = TOption.aliases
  }

  object TOption extends GenericCompanion {
    def aliases: List[TypeName] = List("opt", "option")
  }


  final case class TMap(keyType: ScalarId, valueType: TypeId) extends Generic {
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


