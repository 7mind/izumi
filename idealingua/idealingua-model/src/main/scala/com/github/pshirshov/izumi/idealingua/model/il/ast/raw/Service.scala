package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.AbstractTypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{InterfaceId, ServiceId}

case class Service(id: ServiceId, methods: List[Service.DefMethod])

object Service {

  trait DefMethod

  object DefMethod {

    sealed trait Output

    object Output {
      case class Struct(input: RawSimpleStructure) extends Output
      case class Algebraic(alternatives: RawTypes) extends Output
      case class Singular(typeId: AbstractTypeId) extends Output
    }

    case class Signature(input: RawSimpleStructure, output: Output)

    case class RPCMethod(name: String, signature: Signature) extends DefMethod

    @deprecated
    case class DeprecatedSignature(input: RawInterfaces, output: RawInterfaces) {
      def asList: List[InterfaceId] = input ++ output
    }

    @deprecated
    case class DeprecatedMethod(name: String, signature: DeprecatedSignature) extends DefMethod
  }

}
