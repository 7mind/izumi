package com.github.pshirshov.izumi.idealingua.translator.toscala.products

import com.github.pshirshov.izumi.idealingua.model.common.TypeName
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.runtime.Import

import scala.meta.{Defn, Term}


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

case class CogenPair[T <: Defn](defn: T, companion: Defn.Object) {
  def render: List[Defn] = List(defn, companion)
}

case class CogenServiceProduct(
                           service: CogenPair[Defn.Trait]
                           , client: CogenPair[Defn.Trait]
                           , wrapped: CogenPair[Defn.Trait]
                           , defs: Defn.Object
                           , imports: List[Import]
                         ) extends RenderableCogenProduct {

  override def preamble: String =
    s"""${imports.map(_.render).mkString("\n")}
       |""".stripMargin

  def render: List[Defn] = {
    List(service, client, wrapped).flatMap(_.render) :+ defs
  }
}


object CogenProduct {
  type InterfaceProduct = CogenProduct[Defn.Trait]
  type CompositeProudct = CogenProduct[Defn.Class]
  type IdentifierProudct = CogenProduct[Defn.Class]

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
