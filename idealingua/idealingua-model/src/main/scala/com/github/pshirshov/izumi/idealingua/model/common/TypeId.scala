package com.github.pshirshov.izumi.idealingua.model.common

import com.github.pshirshov.izumi.idealingua.model.common.Primitive.prelude
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._


trait TypeId {
  def pkg: Package

  def name: TypeName

  def isBuiltin: Boolean = pkg.isEmpty && (Primitive.mapping.contains(name) || Generic.all.contains(name))

  override def toString: TypeName = s"${getClass.getSimpleName}:${pkg.mkString(".")}#$name"
}

trait Scalar extends TypeId {
  this: TypeId =>
}

case class UserType(pkg: Package, name: TypeName) extends TypeId {
  def toAlias: AliasId = AliasId(pkg, name)

  def toEnum: EnumId = EnumId(pkg, name)

  def toInterface: InterfaceId = InterfaceId(pkg, name)

  def toDTO: DTOId = DTOId(pkg, name)

  def toIdentifier: IdentifierId = IdentifierId(pkg, name)

  def toService: ServiceId = ServiceId(pkg, name)
}

object UserType {
  def apply(id: TypeId): UserType = new UserType(id.pkg, id.name)

  def parse(s: String): UserType = {
    val parts = s.split('.')
    UserType(parts.toSeq.init, parts.last)
  }
}

object TypeId {

  case class InterfaceId(pkg: Package, name: TypeName) extends TypeId

  case class DTOId(pkg: Package, name: TypeName) extends TypeId

  case class AliasId(pkg: Package, name: TypeName) extends TypeId

  case class EnumId(pkg: Package, name: TypeName) extends TypeId

  case class IdentifierId(pkg: Package, name: TypeName) extends TypeId with Scalar

  case class ServiceId(pkg: Package, name: TypeName) extends TypeId

  case class EphemeralId(parent: TypeId, name: TypeName) extends TypeId {
    override def pkg: Package = parent.pkg :+ parent.name
  }

  //case class SignatureId(pkg: Package, name: TypeName) extends TypeId

}

trait Primitive extends TypeId with Scalar {
}

object Primitive {
  final val prelude: Package = Seq.empty

  case object TString extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "str"
  }

  case object TInt8 extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "i08"
  }

  case object TInt16 extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "i16"
  }

  case object TInt32 extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "i32"
  }

  case object TInt64 extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "i64"
  }

  case object TFloat extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "flt"
  }

  case object TDouble extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "dbl"
  }

  case object TUUID extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "uid"
  }

  case object TTs extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "tsl"
  }


  case object TTsTz extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "tsz"
  }

  case object TTime extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "time"
  }

  case object TDate extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "date"
  }


  final val mapping = Set(
    TString
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

trait Generic extends TypeId {
  def args: List[TypeId]
}

object Generic {

  case class TList(valueType: TypeId) extends Generic {

    override def args: List[TypeId] = List(valueType)

    override def pkg: Package = prelude

    override def name: TypeName = "list"
  }

  case class TSet(valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(valueType)

    override def pkg: Package = prelude

    override def name: TypeName = "set"
  }

  case class TMap(keyType: Scalar, valueType: TypeId) extends Generic {
    override def args: List[TypeId] = List(keyType, valueType)

    override def pkg: Package = prelude

    override def name: TypeName = "map"
  }

  final val all = Set("list", "set", "map")
}
