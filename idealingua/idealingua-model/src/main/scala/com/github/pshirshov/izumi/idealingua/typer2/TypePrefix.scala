package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.typer2.IzTypeId.{IzNamespace, IzPackage}

sealed trait TypePrefix

object TypePrefix {
  case class UserTLT(location: IzPackage) extends TypePrefix
  case class UserT(location: IzPackage, subpath: Seq[IzNamespace]) extends TypePrefix
}
