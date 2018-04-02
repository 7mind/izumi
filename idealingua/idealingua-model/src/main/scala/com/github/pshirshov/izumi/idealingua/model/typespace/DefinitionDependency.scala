package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{InterfaceId, ServiceId}
import com.github.pshirshov.izumi.idealingua.model.common.{TypeId, TypeName}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed

sealed trait DefinitionDependency {
  def typeId: TypeId
}

object DefinitionDependency {

  case class DepField(definedIn: TypeId, typeId: TypeId, tpe: typed.Field) extends DefinitionDependency {
    override def toString: TypeName = s"[field $definedIn::${tpe.name} :$typeId]"
  }

  case class DepPrimitiveField(definedIn: TypeId, typeId: TypeId, tpe: typed.PrimitiveField) extends DefinitionDependency {
    override def toString: TypeName = s"[field $definedIn::${tpe.name} :$typeId]"
  }


  case class DepParameter(definedIn: TypeId, typeId: TypeId) extends DefinitionDependency {
    override def toString: TypeName = s"[param $definedIn::$typeId]"
  }

  case class DepServiceParameter(definedIn: ServiceId, typeId: TypeId) extends DefinitionDependency {
    override def toString: TypeName = s"[sparam $definedIn::$typeId]"
  }


  case class DepInterface(definedIn: TypeId, typeId: InterfaceId) extends DefinitionDependency {
    override def toString: TypeName = s"[interface $definedIn::$typeId]"
  }

  case class DepAlias(definedIn: TypeId, typeId: TypeId) extends DefinitionDependency {
    override def toString: TypeName = s"[alias $definedIn::$typeId]"
  }

}
