package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzName

trait TypePlane {
  def resolve(typeId: IzName): Option[IzType]
}
