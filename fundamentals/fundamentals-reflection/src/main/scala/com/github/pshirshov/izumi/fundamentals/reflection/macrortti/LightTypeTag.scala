package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.{AbstractReference, Lambda}

sealed trait LightTypeTag {
  def combine(o: Seq[LightTypeTag]): AbstractReference = {
    this match {
      case l: Lambda =>
        if (l.input.size > o.size) {
          throw new IllegalArgumentException(s"$this expects no more than ${l.input.size} parameters: ${l.input} but got $o")
        }

        val parameters = l.input.zip(o).map {
          case (p, v: AbstractReference) =>
            p.name -> v
        }.toMap
        RuntimeAPI.applyLambda(l, parameters)
      case _ =>
        throw new IllegalArgumentException(s"$this is not a type lambda, it cannot be parameterized with $o")
    }
  }
}

object LightTypeTag {


  sealed trait AbstractReference extends LightTypeTag

  case class Lambda(input: List[LambdaParameter], output: AbstractReference) extends AbstractReference {
    override def toString: String = s"λ${input.mkString("(", ",", ")")} → $output"
  }

  case class LambdaParameter(name: String, kind: AbstractKind, variance: Variance) {
    override def toString: String = s" %($variance${name.split('.').last} : $kind) "
  }

  sealed trait AppliedReference extends AbstractReference

  case class NameReference(ref: String) extends AppliedReference {
    override def toString: String = ref.split('.').last
  }

  case class FullReference(ref: String, parameters: List[TypeParam]) extends AppliedReference {
    override def toString: String = s"${ref.split('.').last}${parameters.mkString("[", ",", "]")}"
  }

  case class TypeParam(ref: AbstractReference, kind: AbstractKind, variance: Variance) {
    override def toString: String = s" ($variance$ref : $kind) "
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

  sealed trait AbstractKind {
    def boundaries: Boundaries
  }

  object AbstractKind {

    case class Hole(boundaries: Boundaries, variance: Variance) extends AbstractKind {
      override def toString: String = {
        s"${variance}_"
      }
    }

    case class Kind(parameters: List[AbstractKind], boundaries: Boundaries, variance: Variance) extends AbstractKind {
      override def toString: String = {
        val p = parameters.mkString(", ")
        s"${variance}_[$p]"
      }
    }

  }

}
