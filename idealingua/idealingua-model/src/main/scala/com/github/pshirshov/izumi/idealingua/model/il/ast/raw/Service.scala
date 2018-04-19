package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.AbstractIndefiniteId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId

case class Service(id: ServiceId, methods: List[Service.DefMethod])

object Service {

  trait DefMethod

  object DefMethod {

    sealed trait Output

    object Output {
      case class Struct(input: RawSimpleStructure) extends Output
      case class Algebraic(alternatives: List[RawAdtMember]) extends Output
      case class Singular(typeId: AbstractIndefiniteId) extends Output
    }

    case class Signature(input: RawSimpleStructure, output: Output)

    case class RPCMethod(name: String, signature: Signature) extends DefMethod
  }

}
