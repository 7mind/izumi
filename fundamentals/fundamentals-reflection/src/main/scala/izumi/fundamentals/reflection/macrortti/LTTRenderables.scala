package izumi.fundamentals.reflection.macrortti

import izumi.functional.{Renderable, WithRenderableSyntax}
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.SymName.SymLiteral
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef._

trait LTTRenderables extends WithRenderableSyntax {

  def r_SymName(sym: SymName, hasPrefix: Boolean): String

  implicit lazy val r_LightTypeTag: Renderable[LightTypeTagRef] = new Renderable[LightTypeTagRef] {
    override def render(value: LightTypeTagRef): String = value match {
      case a: AbstractReference =>
        a.render()
    }
  }


  implicit lazy val r_AbstractReference: Renderable[AbstractReference] = new Renderable[AbstractReference] {
    override def render(value: AbstractReference): String = value match {
      case a: AppliedReference =>
        a.render()
      case l: Lambda =>
        l.render()
    }
  }


  implicit lazy val r_AppliedReference: Renderable[AppliedReference] = new Renderable[AppliedReference] {
    override def render(value: AppliedReference): String = value match {
      case a: AppliedNamedReference =>
        a.render()
      case u: UnionReference =>
        u.render()
      case i: IntersectionReference =>
        i.render()
      case r: Refinement =>
        r.render()
    }
  }


  implicit lazy val r_Refinement: Renderable[Refinement] = new Renderable[Refinement] {
    override def render(value: Refinement): String = {
      s"(${value.reference.render()} & ${value.decls.map(_.render()).toSeq.sorted.mkString("{", ", ", "}")})"
    }
  }


  implicit lazy val r_RefinementDecl: Renderable[RefinementDecl] = new Renderable[RefinementDecl] {
    override def render(value: RefinementDecl): String = value match {
      case RefinementDecl.Signature(name, input, output) =>
        s"def $name${input.map(_.render()).mkString("(", ", ", ")")}: ${output.render()}"
      case RefinementDecl.TypeMember(name, tpe) =>
        s"type $name = $tpe"
    }
  }

  implicit lazy val r_AppliedNamedReference: Renderable[AppliedNamedReference] = new Renderable[AppliedNamedReference] {
    override def render(value: AppliedNamedReference): String = value match {
      case n: NameReference =>
        n.render()
      case f: FullReference =>
        f.render()
    }
  }

  implicit lazy val r_Lambda: Renderable[Lambda] = new Renderable[Lambda] {
    override def render(value: Lambda): String = {
      s"λ ${value.input.map(_.render()).mkString(",")} → ${value.output.render()}"
    }
  }


  implicit lazy val r_LambdaParameter: Renderable[LambdaParameter] = new Renderable[LambdaParameter] {
    override def render(value: LambdaParameter): String = {
      s"%${value.name}"
    }
  }

  implicit lazy val r_NameRefRenderer: Renderable[NameReference] = new Renderable[NameReference] {
    override def render(value: NameReference): String = {
      val r = r_SymName(value.ref, value.prefix.isDefined)

      val rr = value.boundaries match {
        case _: Boundaries.Defined =>
          s"$r|${value.boundaries.render()}"
        case Boundaries.Empty =>
          r
      }

      value.prefix match {
        case Some(p) =>
          s"${p.render()}::$rr"
        case None =>
          rr
      }
    }
  }

  implicit lazy val r_FullReference: Renderable[FullReference] = new Renderable[FullReference] {
    override def render(value: FullReference): String = {
      s"${value.asName.render()}${value.parameters.map(_.render()).mkString("[", ",", "]")}"
    }
  }


  implicit lazy val r_IntersectionReference: Renderable[IntersectionReference] = new Renderable[IntersectionReference] {
    override def render(value: IntersectionReference): String = {
      value.refs.map(_.render()).mkString("{", " & ", "}")
    }
  }

  implicit lazy val r_UnionReference: Renderable[UnionReference] = new Renderable[UnionReference] {
    override def render(value: UnionReference): String = {
      value.refs.map(_.render()).mkString("{", " | ", "}")
    }
  }

  implicit lazy val r_TypeParam: Renderable[TypeParam] = new Renderable[TypeParam] {
    override def render(value: TypeParam): String = {
      s"${value.variance.render()}${value.ref}"
    }
  }

  implicit lazy val r_Variance: Renderable[Variance] = new Renderable[Variance] {
    override def render(value: Variance): String = value match {
      case Variance.Invariant => "="
      case Variance.Contravariant => "-"
      case Variance.Covariant => "+"
    }
  }

  implicit lazy val r_Boundaries: Renderable[Boundaries] = new Renderable[Boundaries] {
    override def render(value: Boundaries): String = value match {
      case Boundaries.Defined(bottom, top) =>
        s"<${bottom.render()}..${top.render()}>"

      case Boundaries.Empty =>
        ""
    }
  }

}

object LTTRenderables {

  object Short extends LTTRenderables {
    def r_SymName(sym: SymName, @unused hasPrefix: Boolean): String = {
      sym match {
        case SymLiteral(c) => c
        case _ => sym.name.split('.').last
      }
    }
  }

  object Long extends LTTRenderables {
    def r_SymName(sym: SymName, @unused hasPrefix: Boolean): String = {
      sym.name
    }
  }
}
