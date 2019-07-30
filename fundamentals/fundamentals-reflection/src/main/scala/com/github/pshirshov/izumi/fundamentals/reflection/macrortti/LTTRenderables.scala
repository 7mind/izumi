package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.functional.{Renderable, WithRenderableSyntax}
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.{AbstractKind, AbstractReference, Boundaries, FullReference, Lambda, LambdaParameter, NameReference, TypeParam, Variance}

trait LTTRenderables extends WithRenderableSyntax {

  implicit def r_LightTypeTag: Renderable[LightTypeTag] = {
    case a: AbstractReference =>
      a.render()
  }

  implicit def r_AbstractReference: Renderable[AbstractReference] = {
    case l: Lambda =>
      l.render()
    case n: NameReference =>
      n.render()
    case f: FullReference =>
      f.render()

  }


  implicit def r_Lambda: Renderable[Lambda] = (value: Lambda) => {
    s"λ ${value.input.map(_.render()).mkString(",")} → ${value.output.render()}"
  }

  implicit def r_LambdaParameter: Renderable[LambdaParameter] = (value: LambdaParameter) => {
    value.kind match {
      case AbstractKind.Proper =>
        s"%${value.name}"

      case k =>
        s"%(${value.name}: ${k.render()})"
    }
  }

  implicit def r_NameReference: Renderable[NameReference] = nameRefRenderer

  protected def nameRefRenderer: Renderable[NameReference]

  implicit def r_FullReference: Renderable[FullReference] = (value: FullReference) => {
    s"${value.asName.render()}${value.parameters.map(_.render()).mkString("[", ",", "]")}"
  }

  implicit def r_TypeParam: Renderable[TypeParam] = (value: TypeParam) => {
    value.kind match {
      case AbstractKind.Proper =>
        s"${value.variance.render()}${value.ref}"

      case k =>
        s"${value.variance.render()}${value.ref}:${k.render()}"

    }
  }

  implicit def r_Variance: Renderable[Variance] = {
    case Variance.Invariant => "="
    case Variance.Contravariant => "-"
    case Variance.Covariant => "+"
  }

  implicit def r_Boundaries: Renderable[Boundaries] = {
    case Boundaries.Defined(bottom, top) =>
      s" <: ${top.render()} >: ${bottom.render()}"

    case Boundaries.Empty =>
      ""
  }

  implicit def r_AbstractKind: Renderable[AbstractKind] = {
    case AbstractKind.Proper => "*"
    case AbstractKind.Hole(_, variance) => s"${variance.render()}_"
    case AbstractKind.Kind(parameters, _, _) => {
      val p = parameters.map(p => p.render()).mkString(", ")
      s"_[$p]"
    }
  }
}

object LTTRenderables {

  object Short extends LTTRenderables {
    override protected def nameRefRenderer: Renderable[NameReference] = _.ref.split('.').last
  }

  object Long extends LTTRenderables {
    override protected def nameRefRenderer: Renderable[NameReference] = _.ref
  }

}