package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.{AbstractReference, AppliedNamedReference, AppliedReference, Boundaries, FullReference, IntersectionReference, Lambda, LambdaParameter, NameReference, Refinement, RefinementDecl, TypeParam}

protected[macrortti] object RuntimeAPI {

  def unpack(ref: AbstractReference): Set[NameReference] = {
    ref match {
      case Lambda(_, output) =>
        unpack(output)
      case reference: AppliedReference =>
        reference match {
          case reference: AppliedNamedReference =>
            reference match {
              case n: NameReference =>
                Set(n) ++ n.prefix.toSet.flatMap(unpack)
              case f: FullReference =>
                f.parameters.map(_.ref).flatMap(unpack).toSet ++ f.prefix.toSet.flatMap(unpack) + f.asName
            }
          case IntersectionReference(refs) =>
            refs.flatMap(unpack)
          case Refinement(reference, decls) =>
            unpack(reference) ++ decls.flatMap(d => d match {
              case RefinementDecl.Signature(_, input, output) =>
                unpack(output) ++ input.flatMap(unpack)
              case RefinementDecl.TypeMember(_, ref) =>
                unpack(ref)
            })
        }
    }
  }

  def applyLambda(lambda: Lambda, parameters: Map[String, AbstractReference]): AbstractReference = {
    val newParams = lambda.input.filterNot(p => parameters.contains(p.name))

    val rewriter = new Rewriter(parameters)((_, _, v) => v)
    val replaced = rewriter.replaceRefs(lambda.output)

    if (newParams.isEmpty) {
      replaced
    } else {
      val renamed = newParams.zipWithIndex.map {
        case (p, idx) =>
          p.name -> idx.toString
      }
      val nr = newParams.zipWithIndex.map {
        case (_, idx) =>
          LambdaParameter(idx.toString)
      }
      val rewriter = new Rewriter(renamed.toMap)((self, n, v) => {
        NameReference(v, self.replaceBoundaries(n.boundaries), self.replacePrefix(n.prefix))
      })

      Lambda(nr, rewriter.replaceRefs(replaced))
    }
  }

  class Rewriter[T](rules: Map[String, T])(complete: (Rewriter[T], NameReference, T) => AbstractReference) {
    def replaceRefs(reference: AbstractReference): AbstractReference = {
      reference match {
        case l: Lambda =>
          l
        case o: AppliedReference =>
          replaceApplied(o)
      }
    }

    def replacePrefix(prefix: Option[AppliedReference]): Option[AppliedReference] = {
      prefix.map(p => ensureApplied(p, replaceApplied(p)))
    }


    def replaceBoundaries(boundaries: Boundaries): Boundaries = {
      boundaries match {
        case Boundaries.Defined(bottom, top) =>
          Boundaries.Defined(replaceRefs(bottom), replaceRefs(top))
        case Boundaries.Empty =>
          boundaries
      }
    }

    private def replaceApplied(reference: AppliedReference): AbstractReference = {
      reference match {
        case IntersectionReference(refs) =>
          val replaced = refs.map(replaceNamed).map(r => ensureAppliedNamed(reference, r))
          IntersectionReference(replaced)
        case Refinement(base, decls) =>

          val rdecls = decls.map {
            case RefinementDecl.Signature(name, input, output) =>
              RefinementDecl.Signature(name, input.map(p => ensureApplied(reference, replaceRefs(p))), ensureApplied(reference, replaceRefs(output)))
            case RefinementDecl.TypeMember(name, ref) =>
              RefinementDecl.TypeMember(name, replaceRefs(ref))
          }

          Refinement(ensureApplied(base, replaceApplied(base)), rdecls.toSet)
        case n: AppliedNamedReference =>
          replaceNamed(n)
      }
    }

    private def replaceNamed(reference: AppliedNamedReference): AbstractReference = {
      reference match {

        case n@NameReference(ref, boundaries, prefix) =>
          rules.get(ref) match {
            case Some(value) =>
              complete(this, n, value)
            case None =>
              NameReference(ref, replaceBoundaries(boundaries), replacePrefix(prefix))
          }

        case FullReference(ref, parameters, prefix) =>
          val p = parameters.map {
            case TypeParam(pref, variance) =>
              TypeParam(replaceRefs(pref), variance)
          }
          FullReference(ref, p, prefix)
      }
    }

    private def ensureApplied(context: AbstractReference, ref: AbstractReference): AppliedReference = {
      ref match {
        case reference: AppliedReference =>
          reference
        case o =>
          throw new IllegalStateException(s"Expected applied reference but got $o while processing $context")
      }
    }

    private def ensureAppliedNamed(context: AbstractReference, ref: AbstractReference): AppliedNamedReference = {
      ref match {
        case reference: AppliedNamedReference =>
          reference
        case o =>
          throw new IllegalStateException(s"Expected named applied reference but got $o while processing $context")
      }
    }
  }

}
