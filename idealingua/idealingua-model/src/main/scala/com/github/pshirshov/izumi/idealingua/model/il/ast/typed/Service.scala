package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{InterfaceId, ServiceId}

case class Service(id: ServiceId, methods: List[Service.DefMethod])

object Service {

  trait DefMethod

  object DefMethod {

    sealed trait Output

    object Output {

      case class Usual(input: SimpleStructure) extends Output

      case class Algebraic(alternatives: Types) extends Output

    }

    case class Signature(input: SimpleStructure, output: Output)

    case class RPCMethod(name: String, signature: Signature) extends DefMethod

    case class DeprecatedSignature(input: Interfaces, output: Interfaces) {
      def asList: List[InterfaceId] = input ++ output
    }

    case class DeprecatedRPCMethod(name: String, signature: DeprecatedSignature) extends DefMethod

  }

}
