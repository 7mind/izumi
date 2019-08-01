package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag._

import scala.reflect.macros.blackbox

protected[macrortti] trait LTTLiftables {
  val c: blackbox.Context

  import c.universe._

  implicit val liftable_Ns: Liftable[LightTypeTag.type] = { _: LightTypeTag.type => q"${symbolOf[LightTypeTag.type].asClass.module}" }
  implicit val liftable_Invariant: Liftable[Variance.Invariant.type] = { _: Variance.Invariant.type => q"${symbolOf[Variance.Invariant.type].asClass.module}" }
  implicit val liftable_Covariant: Liftable[Variance.Covariant.type] = { _: Variance.Covariant.type => q"${symbolOf[Variance.Covariant.type].asClass.module}" }
  implicit val liftable_Contravariant: Liftable[Variance.Contravariant.type] = { _: Variance.Contravariant.type => q"${symbolOf[Variance.Contravariant.type].asClass.module}" }
  implicit val liftable_Proper: Liftable[LightTypeTag.AbstractKind.Proper.type] = { _: LightTypeTag.AbstractKind.Proper.type => q"${symbolOf[LightTypeTag.AbstractKind.Proper.type].asClass.module}" }

  implicit val lifted_Variance: Liftable[Variance] = Liftable[Variance] {
    case Variance.Invariant => q"${Variance.Invariant}"
    case Variance.Contravariant => q"${Variance.Contravariant}"
    case Variance.Covariant => q"${Variance.Covariant}"
  }

  implicit val lifted_Boundaries: Liftable[Boundaries] = Liftable[Boundaries] {
    case Boundaries.Defined(bottom, top) =>
      q"$LightTypeTag.Boundaries.Defined($bottom, $top)"
    case Boundaries.Empty =>
      q"$LightTypeTag.Boundaries.Empty"
  }

  implicit val lifted_AbstractKind: Liftable[AbstractKind] = Liftable[AbstractKind] {
    case LightTypeTag.AbstractKind.Hole(b, v) =>
      q"$LightTypeTag.AbstractKind.Hole($b, $v)"

    case LightTypeTag.AbstractKind.Kind(parameters, b, v) =>
      q"$LightTypeTag.AbstractKind.Kind($parameters, $b, $v)"

    case LightTypeTag.AbstractKind.Proper =>
      q"${LightTypeTag.AbstractKind.Proper}"
  }

  implicit val lifted_AppliedReference: Liftable[AppliedReference] = Liftable[AppliedReference] {
    case nr: AppliedNamedReference =>
      lifted_AppliedNamedReference.apply(nr)
    case IntersectionReference(p) =>
      q"$LightTypeTag.IntersectionReference($p)"
    case Refinement(ref, decls) =>
      q"$LightTypeTag.Refinement($ref, $decls)"
//    case Contract(ref, boundaries) =>
//      q"$LightTypeTag.Contract($ref, $boundaries)"
  }

  implicit val lifted_RefinementDecl: Liftable[RefinementDecl] = Liftable[RefinementDecl] {
    case RefinementDecl.Signature(name, input, output) =>
      q"$LightTypeTag.RefinementDecl.Signature($name, $input, $output)"
    case RefinementDecl.TypeMember(name, t) =>
      q"$LightTypeTag.RefinementDecl.TypeMember($name, $t)"
  }

  implicit val lifted_AppliedNamedReference: Liftable[AppliedNamedReference] = Liftable[AppliedNamedReference] {
    case nr: NameReference =>
      lifted_NameReference.apply(nr)
    case FullReference(ref, params, prefix) =>
      q"$LightTypeTag.FullReference($ref, $params, $prefix)"
  }

  implicit val lifted_NameReference: Liftable[NameReference] = Liftable[NameReference] {
    nr =>
      q"$LightTypeTag.NameReference(${nr.ref}, ${nr.boundaries}, ${nr.prefix})"
  }

  implicit val lifted_TypeParameter: Liftable[TypeParam] = Liftable[TypeParam] {
    r =>
      q"$LightTypeTag.TypeParam(${r.ref}, ${r.variance})"
  }

  implicit val lifted_LambdaParameter: Liftable[LambdaParameter] = Liftable[LambdaParameter] {
    p =>
      q"$LightTypeTag.LambdaParameter(${p.name})"
  }

  implicit val lifted_AbstractReference: Liftable[AbstractReference] = Liftable[AbstractReference] {
    case Lambda(in, out) =>
      q"$LightTypeTag.Lambda($in, $out)"
    case a: AppliedReference =>
      lifted_AppliedReference.apply(a)
  }

  implicit val lifted_LightTypeTag: Liftable[LightTypeTag] = Liftable[LightTypeTag] {
    case r: AbstractReference =>
      lifted_AbstractReference.apply(r)
  }

  implicit val lifted_FLLT: Liftable[FLTT] = Liftable[FLTT] {
    f =>
      q"new ${typeOf[FLTT]}(${f.t}, () => ${f.basesdb}, () => ${f.idb})"
  }
}
