package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.typer2.IzTypeId.IzName

import scala.language.implicitConversions

object IzNameTools {
  implicit def convert(name: String): IzName  = IzName(name)
}
