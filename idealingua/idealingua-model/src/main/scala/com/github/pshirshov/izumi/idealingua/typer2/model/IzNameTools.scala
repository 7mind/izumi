package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzName

import scala.language.implicitConversions

private[model] object IzNameTools {
  implicit def convert(name: String): IzName  = IzName(name)
}
