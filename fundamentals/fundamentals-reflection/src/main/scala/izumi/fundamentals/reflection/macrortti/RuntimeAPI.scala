package izumi.fundamentals.reflection.macrortti

import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef._

private[izumi] object RuntimeAPI {

  def unpack(ref: AbstractReference): Set[NameReference] = {
    def unpackBoundaries(b: Boundaries): Set[NameReference] = {
      b match {
        case Boundaries.Defined(bottom, top) =>
          unpack(bottom) ++ unpack(top)
        case Boundaries.Empty =>
          Set.empty
      }
    }

    ref match {
      case Lambda(_, output) =>
        unpack(output)
      case reference: AppliedReference =>
        reference match {
          case reference: AppliedNamedReference =>
            reference match {
              case n: NameReference =>
                Set(n.copy(prefix = None, boundaries = Boundaries.Empty)) ++ n.prefix.toSet.flatMap(unpack) ++ unpackBoundaries(n.boundaries)
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

  def applyLambda(lambda: Lambda, parameters: Seq[(String, AbstractReference)]): AbstractReference = {
    val pmap = parameters.toMap

    val replaced = parameters.foldLeft(lambda.output) {
      case (acc, p) =>
      val rewriter = new Rewriter(Seq(p).toMap)
       rewriter.replaceRefs(acc)
    }


    val newParams = lambda.input.filterNot(pmap contains _.name)
    if (newParams.isEmpty) {
      replaced
    } else {
      val out = Lambda(newParams, replaced)
      //assert(out.allArgumentsReferenced, s"bad lambda: $out, ${out.paramRefs}, ${out.referenced}")
      // such lambdas are legal: see "regression test: combine Const Lambda to TagK"
      out
    }
  }

  final class Rewriter(rules: Map[String, AbstractReference]) {
    def complete(@unused context: AppliedNamedReference, ref: AbstractReference): AbstractReference = {
      ref
    }

    def replaceRefs(reference: AbstractReference): AbstractReference = {
      reference match {
        case l: Lambda =>
          val bad = l.input.map(_.name).toSet
          val fixed = new Rewriter(rules.filterKeys(k => !bad.contains(k)).toMap).replaceRefs(l.output)
          l.copy(output = fixed)

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
      def returnFullRef(fixedRef: String, parameters: List[TypeParam], prefix: Option[AppliedReference]): FullReference = {
        val p = parameters.map {
          case TypeParam(pref, variance) =>
            TypeParam(replaceRefs(pref), variance)
        }
        FullReference(fixedRef, p, prefix)
      }

      reference match {
        case n@NameReference(ref, boundaries, prefix) =>
          rules.get(ref.name) match {
            case Some(value) =>
              complete(n, value)
            case None =>
              NameReference(ref, replaceBoundaries(boundaries), replacePrefix(prefix))
          }

        case f@FullReference(ref, parameters, prefix) =>
          rules.get(ref) match {
            case Some(value) =>
              complete(f, value) match {
                case out: Lambda =>
                  val refs = parameters.map(_.ref)
                  out.applySeq(refs)

                case n: NameReference =>
                  // we need this to support fakes only (see LightTypeTagRef#makeFakeParams)
                  returnFullRef(n.ref.name, parameters, prefix)

                case out =>
                  throw new IllegalStateException(s"Lambda expected for context-bound $f, but got $out")

              }
            case None =>
              returnFullRef(ref, parameters, prefix)
          }

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
