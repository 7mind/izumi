package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.typer2.IzTypeId.IzName

trait TypePlane {
  def resolve(typeId: IzName): Option[IzType]
}
