package izumi.fundamentals.reflection.macrortti

import izumi.fundamentals.reflection
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef._

import scala.reflect.macros.blackbox

protected[macrortti] abstract class LTTLiftables[C <: blackbox.Context](val c: C) {

  import c.universe._

  final val lightTypeTag: Tree = q"${symbolOf[reflection.macrortti.LightTypeTag.type].asClass.module}"
  final val lightTypeTagRef: Tree = q"${symbolOf[reflection.macrortti.LightTypeTagRef.type].asClass.module}"
  final val invariant: Tree = q"${symbolOf[Variance.Invariant.type].asClass.module}"
  final val contravariant: Tree = q"${symbolOf[Variance.Contravariant.type].asClass.module}"
  final val covariant: Tree = q"${symbolOf[Variance.Covariant.type].asClass.module}"

  implicit final val lifted_Variance: Liftable[Variance] = Liftable[Variance] {
    case Variance.Invariant => invariant
    case Variance.Contravariant => contravariant
    case Variance.Covariant => covariant
  }

  implicit final val lifted_Boundaries: Liftable[Boundaries] = Liftable[Boundaries] {
    case Boundaries.Defined(bottom, top) =>
      q"$lightTypeTagRef.Boundaries.Defined($bottom, $top)"
    case Boundaries.Empty =>
      q"$lightTypeTagRef.Boundaries.Empty"
  }

  implicit final val lifted_AppliedReference: Liftable[AppliedReference] = Liftable[AppliedReference] {
    case nr: AppliedNamedReference =>
      lifted_AppliedNamedReference(nr)
    case IntersectionReference(p) =>
      q"$lightTypeTagRef.IntersectionReference($p)"
    case Refinement(ref, decls) =>
      q"$lightTypeTagRef.Refinement($ref, $decls)"
  }

  implicit final val lifted_RefinementDecl: Liftable[RefinementDecl] = Liftable[RefinementDecl] {
    case RefinementDecl.Signature(name, input, output) =>
      q"$lightTypeTagRef.RefinementDecl.Signature($name, $input, $output)"
    case RefinementDecl.TypeMember(name, t) =>
      q"$lightTypeTagRef.RefinementDecl.TypeMember($name, $t)"
  }

  implicit final val lifted_AppliedNamedReference: Liftable[AppliedNamedReference] = Liftable[AppliedNamedReference] {
    case nr: NameReference =>
      lifted_NameReference(nr)
    case FullReference(ref, params, prefix) =>
      q"$lightTypeTagRef.FullReference($ref, $params, $prefix)"
  }

  implicit final val lifted_NameReference: Liftable[NameReference] = Liftable[NameReference] {
    nr =>
      q"$lightTypeTagRef.NameReference(${nr.ref}, ${nr.boundaries}, ${nr.prefix})"
  }

  implicit final val lifted_TypeParameter: Liftable[TypeParam] = Liftable[TypeParam] {
    r =>
      q"$lightTypeTagRef.TypeParam(${r.ref}, ${r.variance})"
  }

  implicit final val lifted_LambdaParameter: Liftable[LambdaParameter] = Liftable[LambdaParameter] {
    p =>
      q"$lightTypeTagRef.LambdaParameter(${p.name})"
  }

  implicit final val lifted_AbstractReference: Liftable[AbstractReference] = Liftable[AbstractReference] {
    case Lambda(in, out) =>
      q"$lightTypeTagRef.Lambda($in, $out)"
    case a: AppliedReference =>
      lifted_AppliedReference(a)
  }

  implicit final val lifted_LightTypeTagRef: Liftable[LightTypeTagRef] = Liftable[LightTypeTagRef] {
    case r: AbstractReference =>
      lifted_AbstractReference(r)
  }

  // compare with strings
  // test by running same test 20000 times (after 10k)

  implicit final val lifted_FLLT: Liftable[LightTypeTag] = Liftable[LightTypeTag] {
    f =>
      q"$lightTypeTag.apply(${f.ref}, ${f.basesdb}, ${f.idb})"
  }
}
