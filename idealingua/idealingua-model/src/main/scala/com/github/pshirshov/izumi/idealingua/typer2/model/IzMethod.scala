package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.NodeMeta

sealed trait IzInput

object IzInput {
  final case class Singular(typeref: IzTypeReference) extends IzInput
}

sealed trait IzOutput

object IzOutput {

    sealed trait Basic extends IzOutput

    final case class Singular(typeref: IzTypeReference) extends Basic

    final case class Alternative(success: Basic, failure: Basic) extends IzOutput

}

case class IzMethod(name: String, input: IzInput, output: IzOutput, meta: NodeMeta)
