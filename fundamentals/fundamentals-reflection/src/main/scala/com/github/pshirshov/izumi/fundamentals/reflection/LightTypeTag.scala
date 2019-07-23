package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.LightTypeTag.{AbstractKind, AbstractReference, Boundaries, FullReference, Hole, Kind, NameReference}

import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.macros.blackbox

trait ALTT {
  def t: LightTypeTag
}

case class LTT[T](t: LightTypeTag) extends ALTT

object LTT {
  implicit def apply[T]: ALTT = macro TypeTagExampleImpl.makeTag[T, LTT[T]]
}

case class `LTT[_]`[T[_]](t: LightTypeTag) extends ALTT

object `LTT[_]` {

  trait Fake

  implicit def apply[T[_]]: ALTT = macro TypeTagExampleImpl.makeTag[T[Fake], `LTT[_]`[T]]
}

case class `LTT[A, _ <: A]`[A, T[_ <: A]](t: LightTypeTag) extends ALTT

object `LTT[A, _ <: A]` {
  implicit def apply[A, T[_ <: A]]: ALTT = macro TypeTagExampleImpl.makeTag[T[A], `LTT[A, _ <: A]`[A, T]]
}

case class `LTT[_[_]]`[T[_[_]]](t: LightTypeTag) extends ALTT

object `LTT[_[_]]` {

  trait Fake[F[_[_]]]

  implicit def apply[T[_[_]]]: ALTT = macro TypeTagExampleImpl.makeTag[T[Fake], `LTT[_[_]]`[T]]
}

sealed trait LightTypeTag

object LightTypeTag {

  case class Boundaries(top: LightTypeTag, bottom: LightTypeTag)

  sealed trait AbstractKind extends LightTypeTag {
    def boundaries: Boundaries
  }

  case class Hole(boundaries: Boundaries) extends AbstractKind

  case class Kind(parameters: List[AbstractKind], boundaries: Boundaries) extends AbstractKind

  sealed trait AbstractReference extends LightTypeTag

  case class NameReference(ref: String) extends AbstractReference

  case class FullReference(ref: String, parameters: List[LightTypeTag]) extends AbstractReference

}

class TypeTagExampleImpl(val c: blackbox.Context) {

  import c.universe._

  def makeTag[T: c.WeakTypeTag, TT: c.WeakTypeTag]: c.Expr[ALTT] = {
    import c._
    val wtt = implicitly[WeakTypeTag[T]]
    val tpe = wtt.tpe

    val w = implicitly[WeakTypeTag[TT]]
    
    val out = makeRef(tpe)
    val t = q"new ${w.tpe}($out)"
    c.Expr[ALTT](t)
  }

  private def makeRef(tpe: c.universe.Type): LightTypeTag = {
    val tpef = tpe.dealias.resultType
    val typeSymbol = tpef.typeSymbol

    val out = if (!tpef.takesTypeArgs) {
      assert(tpef.typeParams.isEmpty)
      tpef.typeArgs match {
        case Nil =>
          typeSymbol.typeSignature match {
            case TypeBounds(lo, hi) =>
              Hole(Boundaries(makeRef(lo), makeRef(hi)))
            case _ =>
              NameReference(typeSymbol.fullName)
          }

        case args =>
          typeSymbol.typeSignature match {
            case PolyType(params, TypeBounds(lo, hi)) =>
              val sub = params.map(_.asType.toType).map(makeRef)
              val kinds = sub.collect({ case a: AbstractKind => a })
              if (kinds.size == sub.size) {
                Kind(kinds, Boundaries(makeRef(lo), makeRef(hi)))
              } else {
                c.warning(c.enclosingPosition, s"Unexpected state: $tpe has unexpected shape, will try to fallback but it may not be correct")
                FullReference(typeSymbol.fullName, args.map(makeRef))
              }


            case _ =>
              FullReference(typeSymbol.fullName, args.map(makeRef))

          }

      }

    } else {
      assert(tpef.typeArgs.isEmpty)
      FullReference(typeSymbol.fullName, tpef.typeParams.map(_.asType.toType).map(makeRef))
    }
    out
  }

  protected implicit val liftable_Ns: Liftable[LightTypeTag.type] = { _: LightTypeTag.type => q"${symbolOf[LightTypeTag.type].asClass.module}" }

  protected implicit def lifted_Boundaries: Liftable[Boundaries] = Liftable[Boundaries] {
    b =>
      q"$LightTypeTag.Boundaries(${b.bottom}, ${b.top})"
  }

  protected implicit def lifted_AbstractKind: Liftable[AbstractKind] = Liftable[AbstractKind] {
    case LightTypeTag.Hole(b) =>
      q"$LightTypeTag.Hole($b)"
    case Kind(parameters, b) =>
      q"$LightTypeTag.Kind($parameters, $b)"
  }

  protected implicit def lifted_AbstractReference: Liftable[AbstractReference] = Liftable[AbstractReference] {
    case NameReference(ref) =>
      q"$LightTypeTag.NameReference($ref)"
    case FullReference(ref, parameters) =>
      q"$LightTypeTag.FullReference($ref, $parameters)"
  }

  protected implicit def lifted_LightTypeTag: Liftable[LightTypeTag] = Liftable[LightTypeTag] {
    case r: AbstractReference =>
      implicitly[Liftable[AbstractReference]].apply(r)
    case k: AbstractKind =>
      implicitly[Liftable[AbstractKind]].apply(k)
  }

}
