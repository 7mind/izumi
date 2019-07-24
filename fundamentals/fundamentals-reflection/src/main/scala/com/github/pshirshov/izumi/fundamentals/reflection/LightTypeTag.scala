package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.LightTypeTag._

import scala.collection.mutable
import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.macros.blackbox

class FLTT(val t: ALTT, db: () => Map[AbstractReference, Set[AbstractReference]]) {
  lazy val idb: Map[AbstractReference, Set[AbstractReference]] = db()

  override def toString: String = t.toString


  def canEqual(other: Any): Boolean = other.isInstanceOf[FLTT]

  override def equals(other: Any): Boolean = other match {
    case that: FLTT =>
      (that canEqual this) &&
        t == that.t
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(t)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

trait ALTT {
  def t: LightTypeTag
}

case class LTT[T](t: LightTypeTag) extends ALTT

object LTT {
  implicit def apply[T]: FLTT = macro TypeTagExampleImpl.makeTag[T, LTT[T]]
}

case class `LTT[_]`[T[_]](t: LightTypeTag) extends ALTT

object `LTT[_]` {

  trait Fake

  implicit def apply[T[_]]: FLTT = macro TypeTagExampleImpl.makeTag[T[Fake], `LTT[_]`[T]]
}

case class `LTT[+_]`[T[+ _]](t: LightTypeTag) extends ALTT

object `LTT[+_]` {

  trait Fake

  implicit def apply[T[+ _]]: FLTT = macro TypeTagExampleImpl.makeTag[T[Fake], `LTT[+_]`[T]]
}


case class `LTT[A, _ <: A]`[A, T[_ <: A]](t: LightTypeTag) extends ALTT

object `LTT[A, _ <: A]` {
  implicit def apply[A, T[_ <: A]]: FLTT = macro TypeTagExampleImpl.makeTag[T[A], `LTT[A, _ <: A]`[A, T]]
}

case class `LTT[_[_]]`[T[_[_]]](t: LightTypeTag) extends ALTT

object `LTT[_[_]]` {

  trait Fake[F[_[_]]]

  implicit def apply[T[_[_]]]: FLTT = macro TypeTagExampleImpl.makeTag[T[Fake], `LTT[_[_]]`[T]]
}

sealed trait LightTypeTag

object LightTypeTag {

  sealed trait Variance

  object Variance {

    case object Invariant extends Variance

    case object Contravariant extends Variance

    case object Covariant extends Variance

  }

  sealed trait Boundaries

  object Boundaries {

    case class Defined(bottom: LightTypeTag, top: LightTypeTag) extends Boundaries

    case object Empty extends Boundaries

  }

  sealed trait AbstractKind extends LightTypeTag {
    def boundaries: Boundaries
  }

  case class Hole(boundaries: Boundaries, variance: Variance) extends AbstractKind

  case class Kind(parameters: List[AbstractKind], boundaries: Boundaries, variance: Variance) extends AbstractKind

  sealed trait AbstractReference extends LightTypeTag

  case class NameReference(ref: String) extends AbstractReference

  case class FullReference(ref: String, parameters: List[LightTypeTag]) extends AbstractReference

}

class TypeTagExampleImpl(val c: blackbox.Context) {

  import c.universe._

  def makeTag[T: c.WeakTypeTag, TT: c.WeakTypeTag]: c.Expr[FLTT] = {
    import c._
    val wtt = implicitly[WeakTypeTag[T]]
    val tpe = wtt.tpe

    val w = implicitly[WeakTypeTag[TT]]

    val out = makeRef(tpe, Set(tpe))

    val inh = mutable.HashSet[c.Type]()
    extract(tpe, inh)

    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val inhdb = inh.flatMap {
      i =>
        val iref = makeRef(i, Set(i))
        val allbases = tpeBases(i)
        val out = allbases.map(b => makeRef(b, Set(b)))
          .map(b => iref -> b)
          .collect {
            case (i: AbstractReference, b: AbstractReference) =>
              i -> b
          }

        out

    }.toMultimap


    val t = q"new FLTT(new ${w.tpe}($out), () => $inhdb)"
    c.Expr[FLTT](t)
  }

  private val any: c.WeakTypeTag[Any] = c.weakTypeTag[Any]
  private val obj: c.WeakTypeTag[Object] = c.weakTypeTag[Object]
  private val nothing: c.WeakTypeTag[Nothing] = c.weakTypeTag[Nothing]

  private def extract(tpe: c.universe.Type, inh: mutable.HashSet[c.Type]): Unit = {
    inh ++= tpeBases(tpe)
    tpe.typeArgs.filterNot(inh.contains).foreach {
      a =>
        extract(a, inh)
    }
  }

  /*

      val bases: Set[ShortReference] = if (recurseBases) {

        out
      } else {
        Set.empty
      }
    *
   */
  private def makeRef(tpe: c.universe.Type, path: Set[Type]): LightTypeTag = {
    def makeRef(tpe: Type): LightTypeTag = {
      TypeTagExampleImpl.this.makeRef(tpe, path + tpe)
    }

    def makeBoundaries(b: TypeBoundsApi): Boundaries = {
      if ((b.lo =:= nothing.tpe && b.hi =:= any.tpe) || (path.contains(b.lo) || path.contains(b.hi))) {
        Boundaries.Empty
      } else {
        Boundaries.Defined(makeRef(b.lo), makeRef(b.hi))
      }
    }

    val tpef = tpe.dealias.resultType
    val typeSymbol = tpef.typeSymbol

    val typeSymbolTpe = typeSymbol.asType
    val variance = toVariance(typeSymbolTpe)


    val out = if (tpef.takesTypeArgs) {
      assert(tpef.typeArgs.isEmpty)
      //      println((tpef.typeParams, tpef.typeParams.map(_.asInstanceOf[{def variance: Variance}].variance)))
      FullReference(typeSymbol.fullName, tpef.typeParams.map(_.asType.toType).map(makeRef))
    } else {
      assert(tpef.typeParams.isEmpty)
      tpef.typeArgs match {
        case Nil =>
          typeSymbol.typeSignature match {
            case b: TypeBoundsApi =>
              val boundaries = makeBoundaries(b)
              Hole(boundaries, variance)
            case _ =>
              NameReference(typeSymbol.fullName)
          }

        case args =>
          typeSymbol.typeSignature match {
            case PolyType(params, b: TypeBoundsApi) =>
              val sub = params.map(_.asType.toType).map(makeRef)
              val kinds = sub.collect({ case a: AbstractKind => a })

              if (kinds.size == sub.size) {
                val boundaries = makeBoundaries(b)
                Kind(kinds, boundaries, variance)
              } else {
                c.warning(c.enclosingPosition, s"Unexpected state: $tpe has unexpected shape, will try to fallback but it may not be correct")
                FullReference(typeSymbol.fullName, args.map(makeRef))
              }


            case _ =>
              FullReference(typeSymbol.fullName, args.map(makeRef))

          }

      }
    }
    out
  }

  private def tpeBases(tpe: c.universe.Type): Seq[c.universe.Type] = {
    tpeBases(tpe, tpe.dealias.resultType)
  }

  private def tpeBases(tpe: c.universe.Type, tpef: c.universe.Type): Seq[c.universe.Type] = {
    val higherBases = tpe.baseClasses
    val parameterizedBases = higherBases.filterNot {
      s =>
        val btype = s.asType.toType
        btype =:= any.tpe || btype =:= obj.tpe || btype.erasure =:= tpe.erasure
    }.map(s => tpef.baseType(s))
    val hollowBases = higherBases.map(s => s.asType.toType)
    val allbases = parameterizedBases ++ hollowBases
    allbases
  }

  private def toVariance(typeSymbolTpe: c.universe.TypeSymbol) = {
    if (typeSymbolTpe.isCovariant) {
      Variance.Covariant
    } else if (typeSymbolTpe.isContravariant) {
      Variance.Contravariant
    } else {
      Variance.Invariant
    }
  }

  protected implicit val liftable_Ns: Liftable[LightTypeTag.type] = { _: LightTypeTag.type => q"${symbolOf[LightTypeTag.type].asClass.module}" }
  protected implicit val liftable_Invariant: Liftable[Variance.Invariant.type] = { _: Variance.Invariant.type => q"${symbolOf[Variance.Invariant.type].asClass.module}" }
  protected implicit val liftable_Covariant: Liftable[Variance.Covariant.type] = { _: Variance.Covariant.type => q"${symbolOf[Variance.Covariant.type].asClass.module}" }
  protected implicit val liftable_Contravariant: Liftable[Variance.Contravariant.type] = { _: Variance.Contravariant.type => q"${symbolOf[Variance.Contravariant.type].asClass.module}" }

  protected implicit def lifted_Variance: Liftable[Variance] = Liftable[Variance] {
    case Variance.Invariant => q"${Variance.Invariant}"
    case Variance.Contravariant => q"${Variance.Contravariant}"
    case Variance.Covariant => q"${Variance.Covariant}"
  }

  protected implicit def lifted_Boundaries: Liftable[Boundaries] = Liftable[Boundaries] {
    case Boundaries.Defined(bottom, top) =>
      q"$LightTypeTag.Boundaries.Defined($bottom, $top)"
    case Boundaries.Empty =>
      q"$LightTypeTag.Boundaries.Empty"
  }

  protected implicit def lifted_AbstractKind: Liftable[AbstractKind] = Liftable[AbstractKind] {
    case LightTypeTag.Hole(b, v) =>
      q"$LightTypeTag.Hole($b, $v)"
    case Kind(parameters, b, v) =>
      q"$LightTypeTag.Kind($parameters, $b, $v)"
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

  //  protected implicit def lifted_ShortReference: Liftable[ShortReference] = Liftable[ShortReference] {
  //    case ShortNameReference(ref) =>
  //      q"$LightTypeTag.ShortNameReference($ref)"
  //    case ShortFullReference(ref, parameters) =>
  //      q"$LightTypeTag.ShortFullReference($ref, $parameters)"
  //  }
}
