package izumi.fundamentals.reflection.macrortti

import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.SymName.SymTypeName
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.{AbstractReference, Lambda}

sealed trait LightTypeTagRef {
  final def combine(o: Seq[LightTypeTagRef]): AbstractReference = {
    applyParameters {
      l =>
        l.input
          .zip(o).map {
            case (p, v: AbstractReference) =>
              p.name -> v
          }.toMap
    }
  }

  final def combineNonPos(o: Seq[Option[LightTypeTagRef]]): AbstractReference = {
    applyParameters {
      l =>
        l.input
          .zip(o).flatMap {
            case (p, v) =>
              v match {
                case Some(value: AbstractReference) =>
                  Seq(p.name -> value)
                case None =>
                  Seq.empty
              }
          }.toMap
    }
  }

  final def combine(o: Map[String, LightTypeTagRef]): AbstractReference = {
    val parameters = o.map {
      case (p, v: AbstractReference) =>
        p -> v
    }

    applyParameters(_ => parameters)
  }

  private[this] def applyParameters(p: Lambda => Map[String, AbstractReference]): AbstractReference = {
    this match {
      case l: Lambda =>
        val parameters = p(l)
        if (l.input.size < parameters.size) {
          throw new IllegalArgumentException(s"$this expects no more than ${l.input.size} parameters: ${l.input} but got $parameters")
        }
        val expected = l.input.map(_.name).toSet
        val unknownKeys = parameters.keySet.diff(expected)
        if (unknownKeys.nonEmpty) {
          throw new IllegalArgumentException(s"$this takes parameters: $expected but got unexpected ones: $unknownKeys")
        }

        val applied = RuntimeAPI.applyLambda(l, parameters)
        applied
      case _ =>
        throw new IllegalArgumentException(s"$this is not a type lambda, it cannot be parameterized")
    }
  }
}

object LightTypeTagRef {
  import LTTRenderables.Short._
  //import LTTRenderables.Long._

  sealed trait AbstractReference extends LightTypeTagRef

  final case class Lambda(input: List[LambdaParameter], output: AbstractReference) extends AbstractReference {
    def referenced: Set[NameReference] = RuntimeAPI.unpack(this)
    def paramRefs: Set[NameReference] = input.map(n => NameReference(n.name)).toSet
    def allArgumentsReferenced: Boolean = paramRefs.diff(referenced).isEmpty

    def normalizedParams: List[NameReference] = makeFakeParams.map(_._2)
    def normalizedOutput: AbstractReference = RuntimeAPI.applyLambda(this, makeFakeParams.toMap)

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
    def apply(ref: SymName, boundaries: Boundaries = Boundaries.Empty, prefix: Option[AppliedReference] = None): NameReference =
      new NameReference(ref, boundaries, prefix)
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
