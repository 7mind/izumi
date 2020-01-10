package izumi.fundamentals.reflection.macrortti

import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.SymName.SymTypeName
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef._

import scala.annotation.tailrec

sealed trait LightTypeTagRef {
  final def combine(args: Seq[LightTypeTagRef]): AbstractReference = {
    val out = applySeq(args .map {case v: AbstractReference => v})
    //println(s"combining $this with $args => $out")
    out
  }

  final def combineNonPos(args: Seq[Option[LightTypeTagRef]]): AbstractReference = {
    applyParameters {
      l =>
        l.input.zip(args).flatMap {
          case (p, v) =>
            v match {
              case Some(value: AbstractReference) =>
                Seq(p.name -> value)
              case None =>
                Seq.empty
            }
        }
    }
  }

//  final def combine(args: Map[String, LightTypeTagRef]): AbstractReference = {
//    val parameters = args.map { case (p, v: AbstractReference) => p -> v }
//
//    applyParameters(_ => parameters)
//  }

  final def withoutArgs: AbstractReference = {
    def appliedNamedReference(reference: AppliedNamedReference) = {
      reference match {
        case LightTypeTagRef.NameReference(_, _, _) => reference
        case LightTypeTagRef.FullReference(ref, parameters@_, prefix) => NameReference(SymTypeName(ref), Boundaries.Empty, prefix)
      }
    }

    def appliedReference(reference: AppliedReference): AppliedReference = {
      reference match {
        case reference: AppliedNamedReference => appliedNamedReference(reference)
        case LightTypeTagRef.IntersectionReference(refs) =>
          LightTypeTagRef.IntersectionReference(refs.map(appliedNamedReference))
        case LightTypeTagRef.Refinement(reference, decls) =>
          LightTypeTagRef.Refinement(appliedReference(reference), decls)
      }
    }

    @tailrec
    def go(self: LightTypeTagRef): AbstractReference = {
      self match {
        case Lambda(_, output) =>
          go(output)
        case reference: AppliedReference =>
          appliedReference(reference)
      }
    }

    go(this)
  }

  final def shortName: String = {
    getName(LTTRenderables.Short.r_SymName(_, hasPrefix = false), this)
  }

  final def longName: String = {
    getName(LTTRenderables.Long.r_SymName(_, hasPrefix = false), this)
  }

  @tailrec
  @inline
  private[this] def getName(render: SymName => String, self: LightTypeTagRef): String = {
    self match {
      case Lambda(_, output) => getName(render, output)
      case NameReference(ref, _, _) => render(ref)
      case FullReference(ref, _, _) => render(SymTypeName(ref))
      case IntersectionReference(refs) => refs.map(_.shortName).mkString(" & ")
      case Refinement(reference, _) => getName(render, reference)
    }
  }

  final def getPrefix: Option[LightTypeTagRef] = {
    @tailrec
    @inline
    def getPrefix(self: LightTypeTagRef): Option[LightTypeTagRef] = {
      self match {
        case Lambda(_, output) => getPrefix(output)
        case NameReference(_, _, prefix) => prefix
        case FullReference(_, _, prefix) => prefix
        case IntersectionReference(refs) =>
          val prefixes = refs.map(_.getPrefix).collect {
            case Some(p: AppliedNamedReference) => p
          }
          if (prefixes.nonEmpty) Some(IntersectionReference(prefixes)) else None
        case Refinement(reference, _) => getPrefix(reference)
      }
    }

    getPrefix(this)
  }

  final def typeArgs: List[AbstractReference] = {
    this match {
      case Lambda(input, output) =>
        val params = input.iterator.map(_.name).toSet
        output.typeArgs.filter {
          case n: AppliedNamedReference =>
            !params.contains(n.asName.ref.name)
          case _ =>
            true
        }
      case NameReference(_, _, _) =>
        Nil
      case FullReference(_, parameters, _) =>
        parameters.map(_.ref)
      case IntersectionReference(_) =>
        Nil
      case Refinement(reference, _) =>
        reference.typeArgs
    }
  }
  protected[macrortti] def applySeq(refs: Seq[AbstractReference]): AbstractReference = {
    applyParameters {
      l =>
        l.input.zip(refs).map {
          case (p, v) =>
            p.name -> v
        }
    }
  }
  protected[macrortti] def applyParameters(p: Lambda => Seq[(String, AbstractReference)]): AbstractReference = {
    this match {
      case l: Lambda =>
        val parameters = p(l)
        if (l.input.size < parameters.size) {
          throw new IllegalArgumentException(s"$this expects no more than ${l.input.size} parameters: ${l.input} but got $parameters")
        }
        val expected = l.input.map(_.name).toSet
        val unknownKeys = parameters.map(_._1).toSet.diff(expected)
        if (unknownKeys.nonEmpty) {
          throw new IllegalArgumentException(s"$this takes parameters: $expected but got unexpected ones: $unknownKeys")
        }

        RuntimeAPI.applyLambda(l, parameters)
      case _ =>
        throw new IllegalArgumentException(s"$this is not a type lambda, it cannot be parameterized")
    }
  }
}

object LightTypeTagRef {
  import LTTRenderables.Short._

  sealed trait AbstractReference extends LightTypeTagRef

  final case class Lambda(input: List[LambdaParameter], output: AbstractReference) extends AbstractReference {
    def referenced: Set[NameReference] = RuntimeAPI.unpack(this)
    def paramRefs: Set[NameReference] = input.map(n => NameReference(n.name)).toSet
    def allArgumentsReferenced: Boolean = paramRefs.diff(referenced).isEmpty

    lazy val normalizedParams: List[NameReference] = makeFakeParams.map(_._2)
    lazy val normalizedOutput: AbstractReference = RuntimeAPI.applyLambda(this, makeFakeParams)

    override def equals(obj: Any): Boolean = {
      obj match {
        case l: Lambda =>
          if (input.size == l.input.size) {
            normalizedOutput == l.normalizedOutput
          } else {
            false
          }

        case _ =>
          false
      }
    }

    override def hashCode(): Int = {
      normalizedOutput.hashCode()
    }

    override def toString: String = this.render()

    private def makeFakeParams = {
      input.zipWithIndex.map {
        case (p, idx) =>
          p.name -> NameReference(s"!FAKE_$idx")
      }
    }
  }

  final case class LambdaParameter(name: String) {
    override def toString: String = this.render()
  }

  sealed trait AppliedReference extends AbstractReference

  sealed trait AppliedNamedReference extends AppliedReference {
    def asName: NameReference
  }

  final case class IntersectionReference(refs: Set[AppliedNamedReference]) extends AppliedReference {
    override def toString: String = this.render()
  }

  final case class NameReference(ref: SymName, boundaries: Boundaries, prefix: Option[AppliedReference]) extends AppliedNamedReference {
    override def asName: NameReference = this

    override def toString: String = this.render()
  }
  object NameReference {
    def apply(ref: SymName, boundaries: Boundaries = Boundaries.Empty, prefix: Option[AppliedReference] = None): NameReference = new NameReference(ref, boundaries, prefix)
    def apply(tpeName: String): NameReference = NameReference(SymTypeName(tpeName))
  }

  final case class FullReference(ref: String, parameters: List[TypeParam], prefix: Option[AppliedReference]) extends AppliedNamedReference {
    override def asName: NameReference = NameReference(SymTypeName(ref), prefix = prefix)

    override def toString: String = this.render()
  }
  object FullReference {
    def apply(ref: String, parameters: List[TypeParam], prefix: Option[AppliedReference] = None): FullReference = new FullReference(ref, parameters, prefix)
  }

  final case class TypeParam(ref: AbstractReference, variance: Variance) {
    override def toString: String = this.render()
  }

  sealed trait RefinementDecl
  object RefinementDecl {
    final case class Signature(name: String, input: List[AppliedReference], output: AppliedReference) extends RefinementDecl
    final case class TypeMember(name: String, ref: AbstractReference) extends RefinementDecl
  }

  final case class Refinement(reference: AppliedReference, decls: Set[RefinementDecl]) extends AppliedReference {
    override def toString: String = this.render()
  }

  sealed trait Variance {
    override final def toString: String = this.render()
  }
  object Variance {
    case object Invariant extends Variance
    case object Contravariant extends Variance
    case object Covariant extends Variance
  }

  sealed trait Boundaries {
    override final def toString: String = this.render()
  }
  object Boundaries {
    final case class Defined(bottom: AbstractReference, top: AbstractReference) extends Boundaries
    case object Empty extends Boundaries
  }

  sealed trait SymName {
    def name: String
  }
  object SymName {
    final case class SymTermName(name: String) extends SymName
    final case class SymTypeName(name: String) extends SymName
    final case class SymLiteral(name: String) extends SymName
    object SymLiteral {
      def apply(c: Any): SymLiteral = {
        val constant = c match {
          case s: String => "\"" + s + "\""
          case o => o.toString
        }
        SymLiteral(constant)
      }
    }
  }

}
