package com.github.pshirshov.izumi.idealingua.typer2.model

sealed trait IzTypeReference

object IzTypeReference {
  object model {
    case class IzTypeArgName(name: String)
    case class IzTypeArgValue(ref: IzTypeReference)
    case class IzTypeArg(value: IzTypeArgValue)
  }
  import model._

  final case class Scalar(id: IzTypeId) extends IzTypeReference
  final case class Generic(id: IzTypeId, args: Seq[IzTypeArg]) extends IzTypeReference
}

