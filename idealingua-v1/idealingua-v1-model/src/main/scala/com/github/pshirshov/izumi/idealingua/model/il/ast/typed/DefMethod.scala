package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

sealed trait DefMethod {
  def meta: NodeMeta
}

object DefMethod {

  sealed trait Output

  object Output {
    sealed trait NonAlternativeOutput extends Output

    final case class Void() extends NonAlternativeOutput

    final case class Singular(typeId: TypeId) extends NonAlternativeOutput

    final case class Struct(struct: SimpleStructure) extends NonAlternativeOutput

    final case class Algebraic(alternatives: List[AdtMember]) extends NonAlternativeOutput

    final case class Alternative(success: NonAlternativeOutput, failure: NonAlternativeOutput) extends Output
  }

  final case class Signature(input: SimpleStructure, output: Output)

  final case class RPCMethod(name: String, signature: Signature, meta: NodeMeta) extends DefMethod
}




