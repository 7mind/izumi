package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.AbstractIndefiniteId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId

final case class Service(id: ServiceId, methods: List[Service.DefMethod])

object Service {

  trait DefMethod

  object DefMethod {

    sealed trait Output

    object Output {
      final case class Struct(input: RawSimpleStructure) extends Output
      final case class Algebraic(alternatives: List[RawAdtMember]) extends Output
      final case class Singular(typeId: AbstractIndefiniteId) extends Output
    }

    final case class Signature(input: RawSimpleStructure, output: Output)

    final case class RPCMethod(name: String, signature: Signature) extends DefMethod
  }

}
