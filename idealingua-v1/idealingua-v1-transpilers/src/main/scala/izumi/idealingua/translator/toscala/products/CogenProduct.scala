package izumi.idealingua.translator.toscala.products

import izumi.idealingua.model.common.TypeName
import izumi.idealingua.translator.toscala.types.runtime.Import

import scala.meta.{Defn, Term}


final case class CogenProduct[T <: Defn](
                                          defn: T
                                          , companionBase: Defn.Object
                                          , tools: Defn.Class
                                          , more: List[Defn] = List.empty
                                          , preamble: String = ""
                                        ) extends AccompaniedCogenProduct[T] {
  override def companion: Defn.Object = {
    import izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
    val implicitClass = filterEmptyClasses(List(tools))
    companionBase.appendDefinitions(implicitClass: _*)
  }
}

object CogenProduct {
  type InterfaceProduct = CogenProduct[Defn.Trait]
  type CompositeProduct = CogenProduct[Defn.Class]
  type IdentifierProudct = CogenProduct[Defn.Class]


  final case class TraitProduct(defn: Defn.Trait, more: List[Defn] = List.empty, preamble: String = "") extends MultipleCogenProduct[Defn.Trait]

  final case class EnumProduct
  (
    defn: Defn.Trait
    , companionBase: Defn.Object
    , elements: List[(Term.Name, Defn)]
    , more: List[Defn] = List.empty
    , preamble: String = ""
  ) extends AccompaniedCogenProduct[Defn.Trait] {
    override def companion: Defn.Object = {
      import izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
      companionBase.appendDefinitions(elements.map(_._2))
    }
  }

  final case class AdtElementProduct[T <: Defn]
  (
    name: TypeName
    , defn: T
    , companion: Defn.Object
    , converters: List[Defn.Def]
    , evenMore: List[Defn] = List.empty
    , preamble: String = ""
  ) extends AccompaniedCogenProduct[T] {

    override def more: List[Defn] = evenMore ++ converters
  }

  final case class AdtProduct
  (
    defn: Defn.Trait
    , companionBase: Defn.Object
    , elements: List[AdtElementProduct[Defn.Class]]
    , more: List[Defn] = List.empty
    , preamble: String = ""
  ) extends AccompaniedCogenProduct[Defn.Trait] {
    override def companion: Defn.Object = {
      import izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
      companionBase.appendDefinitions(elements.flatMap(_.render))
    }
  }

  final case class CogenServiceProduct
  (
    server: Defn.Trait
    , client: Defn.Trait
    , clientWrapped: CogenServiceProduct.Pair[Defn.Class]
    , serverWrapped: CogenServiceProduct.Pair[Defn.Class]
    , methods: Defn.Object
    , marshallers: Defn.Object
    , imports: List[Import]

    //    service: CogenServiceProduct.Pair[Defn.Trait]
    //    , client: CogenServiceProduct.Pair[Defn.Trait]
    //    , wrapped: CogenServiceProduct.Pair[Defn.Trait]
    //    , defs: CogenServiceProduct.Defs
  ) extends RenderableCogenProduct {

    override def preamble: String =
      s"""${imports.map(_.render).mkString("\n")}
         |""".stripMargin

    def render: List[Defn] = {
      List(server, client) ++
        List(serverWrapped, clientWrapped).flatMap(_.render) ++
        List(methods, marshallers)
    }
  }

  object CogenServiceProduct {

    //    final case class Defs(defs: Defn.Object, in: Pair[Defn.Trait], out: Pair[Defn.Trait]) {
    //      def render: Defn = {
    //        import izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
    //        defs.prependDefnitions(in.render ++ out.render)
    //      }
    //    }
    //
    final case class Pair[T <: Defn](defn: T, companion: Defn.Object) {
      def render: List[Defn] = List(defn, companion)
    }

  }

}
