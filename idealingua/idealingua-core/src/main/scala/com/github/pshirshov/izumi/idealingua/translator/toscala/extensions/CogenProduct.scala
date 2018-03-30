package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import scala.meta.{Defn, Term}


case class CogenProduct[T <: Defn](defn: T, companion: Defn.Object, tools: Defn.Class) extends AbstractCogenProduct {
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
  type AdtProduct = CogenProduct[Defn.Trait]

  case class EnumProduct(defn: Defn.Trait, companion: Defn.Object, elements: List[(Term.Name, Defn)]) extends AbstractCogenProduct {
    def render: List[Defn] = {
      import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
      List(defn, companion.extendDefinition(elements.map(_._2)))
    }
  }

}
