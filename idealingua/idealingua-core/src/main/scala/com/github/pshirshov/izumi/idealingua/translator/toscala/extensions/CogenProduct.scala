package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.common.TypeName

import scala.meta.{Defn, Term}

trait RenderableCogenProduct {
  def preamble: String
  def render: List[Defn]
  def isEmpty: Boolean = render.isEmpty
}

object RenderableCogenProduct {
  def empty: RenderableCogenProduct = new RenderableCogenProduct {
    override def render: List[Defn] = List.empty
    override def preamble: String = ""
  }
}

trait AbstractCogenProduct[T <: Defn] extends RenderableCogenProduct {
  def more: List[Defn]
  def defn: T
  def companion: Defn.Object
}

case class CogenProduct[T <: Defn](
                                    defn: T
                                    , companion: Defn.Object
                                    , tools: Defn.Class
                                    , more: List[Defn] = List.empty
                                    , preamble: String = ""
                                  ) extends AbstractCogenProduct[T] {
  def render: List[Defn] = {
    import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
    List(defn) ++ more ++ List(companion.extendDefinition(tools))
  }
}

object CogenProduct {
  type InterfaceProduct = CogenProduct[Defn.Trait]
  type CompositeProudct = CogenProduct[Defn.Class]
  type IdentifierProudct = CogenProduct[Defn.Class]
  type ServiceProudct = CogenProduct[Defn.Trait]

  case class EnumProduct(
                          defn: Defn.Trait
                          , companion: Defn.Object
                          , elements: List[(Term.Name, Defn)]
                          , more: List[Defn] = List.empty
                          , preamble: String = ""
                        ) extends AbstractCogenProduct[Defn.Trait] {
    def render: List[Defn] = {
      import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
      List(defn) ++ more ++ List(companion.extendDefinition(elements.map(_._2)))
    }
  }

  case class AdtElementProduct[T <: Defn](
                                           name: TypeName
                                           , defn: T
                                           , companion: Defn.Object
                                           , converters: List[Defn.Def]
                                           , more: List[Defn] = List.empty
                                           , preamble: String = ""
                                         ) extends AbstractCogenProduct[T] {
    override def render: List[Defn] = List(defn) ++ more ++ converters ++ List(companion)
  }

  case class AdtProduct(
                         defn: Defn.Trait
                         , companion: Defn.Object
                         , elements: List[AdtElementProduct[Defn.Class]]
                         , more: List[Defn] = List.empty
                         , preamble: String = ""
                       ) extends AbstractCogenProduct[Defn.Trait] {
    def render: List[Defn] = {
      import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
      List(defn) ++ more ++ List(companion.extendDefinition(elements.flatMap(_.render)))
    }
  }

}
