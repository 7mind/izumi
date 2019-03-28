package com.github.pshirshov.izumi.idealingua.typer2.model

sealed trait IzTypeId {
  import IzTypeId.model._
  def name: IzName
}

object IzTypeId {
  object model {
    case class IzDomainPath(name: String)
    case class IzNamespace(name: String)
    case class IzName(name: String)
    case class IzPackage(path: Seq[IzDomainPath])
  }
  import model._

  final case class BuiltinTypeId(name: IzName) extends IzTypeId
  final case class UserTypeId(prefix: TypePrefix, name: IzName) extends IzTypeId

}
