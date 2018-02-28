package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._

case class ILParsedId(pkg: Seq[String], name: String) {
  def toDomainId: DomainId = DomainId(pkg, name)

  def toEnumId: EnumId = EnumId(pkg, name)

  def toAliasId: AliasId = AliasId(pkg, name)

  def toIdId: IdentifierId = IdentifierId(pkg, name)

  def toMixinId: InterfaceId = InterfaceId(pkg, name)

  def toDataId: DTOId = DTOId(pkg, name)

  def toAdtId: AdtId = AdtId(pkg, name)

  def toTypeId: TypeId = {
    downcast(UserType(pkg, name))
  }

  def toServiceId: ServiceId = ServiceId(pkg, name)

  private def isPrimitive: Boolean = pkg.isEmpty && Primitive.mapping.contains(name)

  private def isGeneric: Boolean = pkg.isEmpty && Generic.all.contains(name)


  def toGeneric(params: Seq[Seq[TypeId]]): TypeId = {
    if (isGeneric) {
      name match {
        case "set" =>
          Generic.TSet(params.flatten.head)

        case "list" =>
          Generic.TList(params.flatten.head)

        case "opt" =>
          Generic.TOption(params.flatten.head)

        case "map" =>
          Generic.TMap(toScalar(params.flatten.head), params.flatten.last)
      }
    } else {
      toTypeId
    }
  }

  private def downcast(tid: TypeId): TypeId = {
    if (isPrimitive) {
      Primitive.mapping(tid.name)
    } else {
      tid
    }
  }

  private def toScalar(typeId: TypeId): Scalar = {
    typeId match {
      case p: Primitive =>
        p
      case _ =>
        ILParsedId(typeId.pkg, typeId.name).toIdId
    }
  }
}

object ILParsedId {
  def apply(name: String): ILParsedId = new ILParsedId(Seq.empty, name)

  def apply(pkg: Seq[String]): ILParsedId = new ILParsedId(pkg.init, pkg.last)
}
