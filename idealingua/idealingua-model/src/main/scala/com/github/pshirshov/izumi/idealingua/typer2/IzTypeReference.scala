package com.github.pshirshov.izumi.idealingua.typer2

sealed trait IzTypeReference

object IzTypeReference {
  final case class Scalar(id: IzTypeId) extends IzTypeReference
  final case class Generic(id: IzTypeId, args: Seq[IzTypeArg]) extends IzTypeReference

  case class IzTypeArgName(name: String)
  case class IzTypeArgValue(name: IzType)
  case class IzTypeArg(name: IzTypeArgName, value: IzTypeArgValue)
}

