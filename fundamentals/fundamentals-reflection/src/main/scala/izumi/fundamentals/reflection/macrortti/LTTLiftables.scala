package izumi.fundamentals.reflection.macrortti

import izumi.fundamentals.reflection.macrortti.LightTypeTagRef._

import scala.reflect.macros.blackbox

protected[macrortti] trait LTTLiftables {
  val c: blackbox.Context

  import c.universe._

  implicit val liftable_Ns: Liftable[LightTypeTagRef.type] = { _: LightTypeTagRef.type => q"${symbolOf[LightTypeTagRef.type].asClass.module}" }
  implicit val liftable_Invariant: Liftable[Variance.Invariant.type] = { _: Variance.Invariant.type => q"${symbolOf[Variance.Invariant.type].asClass.module}" }
  implicit val liftable_Covariant: Liftable[Variance.Covariant.type] = { _: Variance.Covariant.type => q"${symbolOf[Variance.Covariant.type].asClass.module}" }
  implicit val liftable_Contravariant: Liftable[Variance.Contravariant.type] = { _: Variance.Contravariant.type => q"${symbolOf[Variance.Contravariant.type].asClass.module}" }

  implicit val lifted_Variance: Liftable[Variance] = Liftable[Variance] {
    case Variance.Invariant => q"${Variance.Invariant}"
    case Variance.Contravariant => q"${Variance.Contravariant}"
    case Variance.Covariant => q"${Variance.Covariant}"
  }

  implicit val lifted_Boundaries: Liftable[Boundaries] = Liftable[Boundaries] {
    case Boundaries.Defined(bottom, top) =>
      q"$LightTypeTagRef.Boundaries.Defined($bottom, $top)"
    case Boundaries.Empty =>
      q"$LightTypeTagRef.Boundaries.Empty"
  }

  implicit val lifted_AppliedReference: Liftable[AppliedReference] = Liftable[AppliedReference] {
    case nr: AppliedNamedReference =>
      lifted_AppliedNamedReference.apply(nr)
    case IntersectionReference(p) =>
      q"$LightTypeTagRef.IntersectionReference($p)"
    case Refinement(ref, decls) =>
      q"$LightTypeTagRef.Refinement($ref, $decls)"
  }

  implicit val lifted_RefinementDecl: Liftable[RefinementDecl] = Liftable[RefinementDecl] {
    case RefinementDecl.Signature(name, input, output) =>
      q"$LightTypeTagRef.RefinementDecl.Signature($name, $input, $output)"
    case RefinementDecl.TypeMember(name, t) =>
      q"$LightTypeTagRef.RefinementDecl.TypeMember($name, $t)"
  }

  implicit val lifted_AppliedNamedReference: Liftable[AppliedNamedReference] = Liftable[AppliedNamedReference] {
    case nr: NameReference =>
      lifted_NameReference.apply(nr)
    case FullReference(ref, params, prefix) =>
      q"$LightTypeTagRef.FullReference($ref, $params, $prefix)"
  }

  implicit val lifted_NameReference: Liftable[NameReference] = Liftable[NameReference] {
    nr =>
      q"$LightTypeTagRef.NameReference(${nr.ref}, ${nr.boundaries}, ${nr.prefix})"
  }

  implicit val lifted_TypeParameter: Liftable[TypeParam] = Liftable[TypeParam] {
    r =>
      q"$LightTypeTagRef.TypeParam(${r.ref}, ${r.variance})"
  }

  implicit val lifted_LambdaParameter: Liftable[LambdaParameter] = Liftable[LambdaParameter] {
    p =>
      q"$LightTypeTagRef.LambdaParameter(${p.name})"
  }

  implicit val lifted_AbstractReference: Liftable[AbstractReference] = Liftable[AbstractReference] {
    case Lambda(in, out) =>
      q"$LightTypeTagRef.Lambda($in, $out)"
    case a: AppliedReference =>
      lifted_AppliedReference.apply(a)
  }

  implicit val lifted_LightTypeTag: Liftable[LightTypeTagRef] = Liftable[LightTypeTagRef] {
    case r: AbstractReference =>
      lifted_AbstractReference.apply(r)
  }

  implicit val lifted_FLLT: Liftable[LightTypeTag] = Liftable[LightTypeTag] {
    f =>
      q"new ${typeOf[LightTypeTag]}(${f.ref}, () => ${f.basesdb}, () => ${f.idb})"
  }
}
