package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId

final case class Service(id: ServiceId, methods: List[Service.DefMethod], doc: Option[String])

object Service {

  trait DefMethod

  object DefMethod {

    sealed trait Output

    object Output {

      final case class Struct(struct: SimpleStructure) extends Output

      final case class Algebraic(alternatives: List[AdtMember]) extends Output

      final case class Singular(typeId: TypeId) extends Output
    }

    final case class Signature(input: SimpleStructure, output: Output)

    final case class RPCMethod(name: String, signature: Signature, doc: Option[String]) extends DefMethod
  }

}
