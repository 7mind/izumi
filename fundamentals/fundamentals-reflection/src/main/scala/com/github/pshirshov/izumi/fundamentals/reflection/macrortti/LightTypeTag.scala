package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.{AbstractReference, Lambda}

sealed trait LightTypeTag {
  def combine(o: Seq[LightTypeTag]): AbstractReference = {
    applyParameters {
      l =>
        l.input.zip(o).map {
          case (p, v: AbstractReference) =>
            p.name -> v
        }.toMap
    }
  }

  def combineNonPos(o: Seq[Option[LightTypeTag]]): AbstractReference = {
    applyParameters {
      l =>
        l.input.zip(o).flatMap {
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

  def combine(o: Map[String, LightTypeTag]): AbstractReference = {
    val parameters = o.map {
      case (p, v: AbstractReference) =>
        p -> v
    }

    applyParameters(_ => parameters)
  }

  private def applyParameters(p: Lambda => Map[String, AbstractReference]): AbstractReference = {
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





object LightTypeTag {


  import LTTRenderables.Short._


  sealed trait AbstractReference extends LightTypeTag

  case class Lambda(input: List[LambdaParameter], output: AbstractReference) extends AbstractReference {
    override def toString: String = this.render()
  }

  case class LambdaParameter(name: String, kind: AbstractKind) {
    override def toString: String = this.render()
  }

  sealed trait AppliedReference extends AbstractReference

  sealed trait AppliedNamedReference extends AppliedReference {
    def asName: NameReference
  }

  case class IntersectionReference(refs: Set[AppliedNamedReference]) extends AppliedReference {
    override def toString: String = this.render()
  }

  case class NameReference(ref: String, prefix: Option[AppliedNamedReference]) extends AppliedNamedReference {

    override def asName: NameReference = this

    override def toString: String = this.render()
  }

  case class FullReference(ref: String, prefix: Option[AppliedNamedReference], parameters: List[TypeParam]) extends AppliedNamedReference {

    override def asName: NameReference = NameReference(ref, prefix)

    override def toString: String = this.render()
  }

  case class TypeParam(ref: AbstractReference, kind: AbstractKind, variance: Variance) {
    override def toString: String = this.render()
  }


  sealed trait Variance {
    override def toString: String = this.render()
  }

  object Variance {

    case object Invariant extends Variance

    case object Contravariant extends Variance

    case object Covariant extends Variance

  }

  sealed trait Boundaries {
    override def toString: String = this.render()
  }

  object Boundaries {

    case class Defined(bottom: LightTypeTag, top: LightTypeTag) extends Boundaries

    case object Empty extends Boundaries

  }

  sealed trait AbstractKind {
    override def toString: String = this.render()
  }

  object AbstractKind {

    case object Proper extends AbstractKind

    case class Hole(boundaries: Boundaries, variance: Variance) extends AbstractKind

    case class Kind(parameters: List[AbstractKind], boundaries: Boundaries, variance: Variance) extends AbstractKind

  }

}
