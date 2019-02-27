package com.github.pshirshov.izumi.idealingua.typer2

sealed trait IzTypeId {
  import IzTypeId._
  def name: IzName
}

object IzTypeId {
  final case class BuiltinType(name: IzName) extends IzTypeId
  final case class UserType(prefix: TypePrefix, name: IzName) extends IzTypeId


  // impl
  case class IzDomainPath(name: String)
  case class IzNamespace(name: String)

  case class IzName(name: String)

  case class IzPackage(path: Seq[IzDomainPath])
}
