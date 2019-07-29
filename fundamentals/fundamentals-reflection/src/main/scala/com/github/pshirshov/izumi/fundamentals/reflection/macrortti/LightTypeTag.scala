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


  sealed trait AbstractReference extends LightTypeTag

  case class Lambda(input: List[LambdaParameter], output: AbstractReference) extends AbstractReference {
    override def toString: String = s"λ ${input.mkString(",")} → $output"
  }

  case class LambdaParameter(name: String, kind: AbstractKind) {
    override def toString: String = {
      kind match {
        case AbstractKind.Proper =>
          s"%$name"

        case k =>
          s"%($name: $k)"
      }
    }
  }

  sealed trait AppliedReference extends AbstractReference {
    def asName: NameReference
  }

  case class NameReference(ref: String, prefix: Option[AppliedReference]) extends AppliedReference {

    override def asName: NameReference = this

    override def toString: String = ref.split('.').last
  }

  case class FullReference(ref: String, prefix: Option[AppliedReference],  parameters: List[TypeParam]) extends AppliedReference {

    override def asName: NameReference = NameReference(ref, prefix)

    override def toString: String = s"${ref.split('.').last}${parameters.mkString("[", ",", "]")}"
  }

  case class TypeParam(ref: AbstractReference, kind: AbstractKind, variance: Variance) {
    override def toString: String = {
      kind match {
        case AbstractKind.Proper =>
          s"$variance$ref"

        case k =>
          s"$variance$ref:$k"

      }
    }
  }


  sealed trait Variance

  object Variance {

    case object Invariant extends Variance {
      override def toString: String = "="
    }

    case object Contravariant extends Variance {
      override def toString: String = "-"
    }

    case object Covariant extends Variance {
      override def toString: String = "+"
    }

  }

  sealed trait Boundaries

  object Boundaries {

    case class Defined(bottom: LightTypeTag, top: LightTypeTag) extends Boundaries {
      override def toString: String = s" <: $top >: $bottom"
    }

    case object Empty extends Boundaries {
      override def toString: String = ""
    }

  }

  sealed trait AbstractKind

  object AbstractKind {
    case object Proper extends AbstractKind {
      override def toString: String = "*"
    }

    case class Hole(boundaries: Boundaries, variance: Variance) extends AbstractKind {
      override def toString: String = {
        s"${variance}_"
      }
    }

    case class Kind(parameters: List[AbstractKind], boundaries: Boundaries, variance: Variance) extends AbstractKind {
      override def toString: String = {
        val p = parameters.mkString(", ")
        s"_[$p]"
      }
    }

  }

}
