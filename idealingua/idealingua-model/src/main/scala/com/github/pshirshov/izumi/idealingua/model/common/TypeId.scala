package com.github.pshirshov.izumi.idealingua.model.common

import com.github.pshirshov.izumi.idealingua.model.common.Primitive.prelude
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._


trait TypeId {
  def pkg: Package

  def name: TypeName

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
  def parse(s: String): UserType = {
    val parts = s.split('.')
    UserType(parts.toSeq.init, parts.last)
  }
}

object TypeId {
  implicit class TypeIdExtensions(tid: TypeId) {
    def toJava: JavaType = JavaType(tid.pkg, tid.name)
  }

  case class InterfaceId(pkg: Package, name: TypeName) extends TypeId

  case class DTOId(pkg: Package, name: TypeName) extends TypeId

  case class AliasId(pkg: Package, name: TypeName) extends TypeId
  
  case class EnumId(pkg: Package, name: TypeName) extends TypeId

  case class IdentifierId(pkg: Package, name: TypeName) extends TypeId with Scalar

  case class ServiceId(pkg: Package, name: TypeName) extends TypeId

  case class EphemeralId(parent: TypeId, name: TypeName) extends TypeId {
    override def pkg: Package = parent.pkg :+  parent.name
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

  case object TInt32 extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "i32"
  }

  case object TInt64 extends Primitive {
    override def pkg: Package = prelude

    override def name: TypeName = "i64"
  }

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

}
