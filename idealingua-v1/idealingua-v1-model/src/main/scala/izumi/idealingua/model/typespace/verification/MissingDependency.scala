package izumi.idealingua.model.typespace.verification

import izumi.idealingua.model.common.TypeId
import izumi.idealingua.model.common.TypeId.{InterfaceId, ServiceId}
import izumi.idealingua.model.il.ast.typed

sealed trait MissingDependency {
  def missing: TypeId
}

object MissingDependency {

  final case class DepField(definedIn: TypeId, missing: TypeId, tpe: typed.Field) extends MissingDependency {
    override def toString: String = s"[field $definedIn::${tpe.name} :$missing]"
  }

  final case class DepPrimitiveField(definedIn: TypeId, missing: TypeId, tpe: typed.IdField) extends MissingDependency {
    override def toString: String = s"[field $definedIn::${tpe.name} :$missing]"
  }

  final case class DepParameter(definedIn: TypeId, missing: TypeId) extends MissingDependency {
    override def toString: String = s"[param $definedIn::$missing]"
  }

  final case class DepServiceParameter(definedIn: ServiceId, method: String, missing: TypeId) extends MissingDependency {
    override def toString: String = s"[sparam $definedIn::$missing]"
  }

  final case class DepInterface(definedIn: TypeId, missing: InterfaceId) extends MissingDependency {
    override def toString: String = s"[interface $definedIn::$missing]"
  }

  final case class DepAlias(definedIn: TypeId, missing: TypeId) extends MissingDependency {
    override def toString: String = s"[alias $definedIn::$missing]"
  }

}
