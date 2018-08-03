package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

sealed trait DefMethod {
  def meta: NodeMeta
}

object DefMethod {

  sealed trait Output

  object Output {

    final case class Struct(struct: SimpleStructure) extends Output

    final case class Algebraic(alternatives: List[AdtMember]) extends Output

    final case class Singular(typeId: TypeId) extends Output

    final case class Void() extends Output
  }

  final case class Signature(input: SimpleStructure, output: Output)

  final case class RPCMethod(name: String, signature: Signature, meta: NodeMeta) extends DefMethod
}




