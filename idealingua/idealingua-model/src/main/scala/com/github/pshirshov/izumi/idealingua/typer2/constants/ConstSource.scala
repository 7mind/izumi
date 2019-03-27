package com.github.pshirshov.izumi.idealingua.typer2.constants

import com.github.pshirshov.izumi.idealingua.typer2.model.{TypedConst, TypedConstId}

trait ConstSource {
  def get(id: TypedConstId): TypedConst
}
