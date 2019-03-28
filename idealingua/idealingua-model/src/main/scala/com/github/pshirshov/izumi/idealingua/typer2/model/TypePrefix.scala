package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.{IzNamespace, IzPackage}

sealed trait TypePrefix

object TypePrefix {
  case class UserTLT(location: IzPackage) extends TypePrefix
  case class UserT(location: IzPackage, subpath: Seq[IzNamespace]) extends TypePrefix
}
