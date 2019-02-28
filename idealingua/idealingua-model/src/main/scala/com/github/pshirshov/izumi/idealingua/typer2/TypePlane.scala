package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzName
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType

trait TypePlane {
  def resolve(typeId: IzName): Option[IzType]
}
