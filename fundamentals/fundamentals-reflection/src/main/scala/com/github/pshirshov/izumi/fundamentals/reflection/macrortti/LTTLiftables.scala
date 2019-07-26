package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag._

import scala.reflect.macros.blackbox

trait LTTLiftables {
  val c: blackbox.Context

  import c.universe._

  implicit val liftable_Ns: Liftable[LightTypeTag.type] = { _: LightTypeTag.type => q"${symbolOf[LightTypeTag.type].asClass.module}" }
  implicit val liftable_Invariant: Liftable[Variance.Invariant.type] = { _: Variance.Invariant.type => q"${symbolOf[Variance.Invariant.type].asClass.module}" }
  implicit val liftable_Covariant: Liftable[Variance.Covariant.type] = { _: Variance.Covariant.type => q"${symbolOf[Variance.Covariant.type].asClass.module}" }
  implicit val liftable_Contravariant: Liftable[Variance.Contravariant.type] = { _: Variance.Contravariant.type => q"${symbolOf[Variance.Contravariant.type].asClass.module}" }
  implicit val liftable_Proper: Liftable[LightTypeTag.AbstractKind.Proper.type] = { _: LightTypeTag.AbstractKind.Proper.type => q"${symbolOf[LightTypeTag.AbstractKind.Proper.type].asClass.module}" }

  implicit def lifted_Variance: Liftable[Variance] = Liftable[Variance] {
    case Variance.Invariant => q"${Variance.Invariant}"
    case Variance.Contravariant => q"${Variance.Contravariant}"
    case Variance.Covariant => q"${Variance.Covariant}"
  }

  implicit def lifted_Boundaries: Liftable[Boundaries] = Liftable[Boundaries] {
    case Boundaries.Defined(bottom, top) =>
      q"$LightTypeTag.Boundaries.Defined($bottom, $top)"
    case Boundaries.Empty =>
      q"$LightTypeTag.Boundaries.Empty"
  }

  implicit def lifted_AbstractKind: Liftable[AbstractKind] = Liftable[AbstractKind] {
    case LightTypeTag.AbstractKind.Hole(b, v) =>
      q"$LightTypeTag.AbstractKind.Hole($b, $v)"

    case LightTypeTag.AbstractKind.Kind(parameters, b, v) =>
      q"$LightTypeTag.AbstractKind.Kind($parameters, $b, $v)"

    case LightTypeTag.AbstractKind.Proper => q"${LightTypeTag.AbstractKind.Proper}"


  }

  implicit def lifted_AppliedReference: Liftable[AppliedReference] = Liftable[AppliedReference] {
    case nr: NameReference =>
      implicitly[Liftable[NameReference]].apply(nr)
    case FullReference(ref, parameters) =>
      q"$LightTypeTag.FullReference($ref, $parameters)"
  }

  implicit def lifted_NameReference: Liftable[NameReference] = Liftable[NameReference] {
    nr =>
      q"$LightTypeTag.NameReference(${nr.ref})"
  }

  implicit def lifted_TypeParameter: Liftable[TypeParam] = Liftable[TypeParam] {
    r =>
      q"$LightTypeTag.TypeParam(${r.ref}, ${r.kind}, ${r.variance})"
  }

  implicit def lifted_LambdaParameter: Liftable[LambdaParameter] = Liftable[LambdaParameter] {
    p =>
      q"$LightTypeTag.LambdaParameter(${p.name}, ${p.kind})"
  }

  implicit def lifted_AbstractReference: Liftable[AbstractReference] = Liftable[AbstractReference] {
    case Lambda(in, out) =>
      q"$LightTypeTag.Lambda($in, $out)"
    case a: AppliedReference =>
      implicitly[Liftable[AppliedReference]].apply(a)


  }

  implicit def lifted_LightTypeTag: Liftable[LightTypeTag] = Liftable[LightTypeTag] {
    case r: AbstractReference =>
      implicitly[Liftable[AbstractReference]].apply(r)
  }
}
