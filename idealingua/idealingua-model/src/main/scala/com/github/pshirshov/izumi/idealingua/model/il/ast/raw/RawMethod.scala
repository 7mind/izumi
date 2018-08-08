package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.AbstractIndefiniteId

sealed trait RawMethod {
  def meta: RawNodeMeta
}

object RawMethod {

  sealed trait Output

  object Output {
    final case class Struct(input: RawSimpleStructure) extends Output
    final case class Algebraic(alternatives: List[RawAdtMember]) extends Output
    final case class Singular(typeId: AbstractIndefiniteId) extends Output
    final case class Void() extends Output
  }

  final case class Signature(input: RawSimpleStructure, output: Output)

  final case class RPCMethod(name: String, signature: Signature, meta: RawNodeMeta) extends RawMethod
}




