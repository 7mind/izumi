package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import scala.meta.Defn

case class CogenProduct[T <: Defn](defn: T, companion: Defn.Object, tools: Defn.Class) {
  def render: List[Defn] = {
    import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
    List(defn, companion.extendDefinition(tools))
  }
}

object CogenProduct {
  type InterfaceProduct = CogenProduct[Defn.Trait]
  type CompositeProudct = CogenProduct[Defn.Class]
  type IdentifierProudct = CogenProduct[Defn.Class]
  type ServiceProudct = CogenProduct[Defn.Trait]
}
