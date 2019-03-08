package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzName

sealed trait IzTypeReference {
  def id: IzTypeId
}

object IzTypeReference {
  object model {
    case class IzTypeArgName(name: String)
    case class IzTypeArgValue(ref: IzTypeReference)
    case class IzTypeArg(value: IzTypeArgValue)
    case class RefToTLTLink(ref: IzTypeReference.Generic, target: IzTypeId.UserType)
  }
  import model._

  final case class Scalar(id: IzTypeId) extends IzTypeReference
  final case class Generic(id: IzTypeId, args: Seq[IzTypeArg], adhocName: Option[IzName]) extends IzTypeReference
}


