package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.AbstractKind.{Hole, Kind}
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag._

import scala.collection.mutable
import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.macros.blackbox

class FLTT(val t: LightTypeTag, db: () => Map[NameReference, Set[NameReference]]) {
  lazy val idb: Map[NameReference, Set[NameReference]] = db()

  def combine(o: FLTT*): FLTT = {
    new FLTT(t.combine(o.map(_.t)), () => Map.empty)
  }

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

object LTT {
  implicit def apply[T]: FLTT = macro LightTypeTagImpl.makeTag[T]
}

object `LTT[_]` {

  trait Fake

  implicit def apply[T[_]]: FLTT = macro LightTypeTagImpl.makeTag[T[Fake]]
}

object `LTT[+_]` {

  trait Fake

  implicit def apply[T[+ _]]: FLTT = macro LightTypeTagImpl.makeTag[T[Fake]]
}

object `LTT[A, _ <: A]` {
  implicit def apply[A, T[_ <: A]]: FLTT = macro LightTypeTagImpl.makeTag[T[A]]
}

object `LTT[_[_]]` {

  trait Fake[F[_[_]]]

  implicit def apply[T[_[_]]]: FLTT = macro LightTypeTagImpl.makeTag[T[Fake]]
}


final class LightTypeTagImpl(val c: blackbox.Context) extends LTTLiftables {

  import c.universe._

  def makeTag[T: c.WeakTypeTag]: c.Expr[FLTT] = {
    import c._
    val wtt = implicitly[WeakTypeTag[T]]
    val tpe = wtt.tpe

    //val w = implicitly[WeakTypeTag[TT]]

    val out = makeRef(tpe, Set(tpe), Map.empty)
    val inh = allTypeReferences(tpe)

    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val inhdb = inh
      .filterNot(p => p.takesTypeArgs || p.typeArgs.nonEmpty || p.typeParams.nonEmpty)
      .flatMap {
        i =>
          val iref = NameReference(i.dealias.resultType.typeSymbol.fullName)
          val allbases = tpeBases(i)
          allbases.map(b => iref -> NameReference(b.dealias.resultType.typeSymbol.fullName))
      }
      .toMultimap
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    println(inhdb.toSeq.niceList())
    println(inhdb.size)
    val t = q"new FLTT($out, () => $inhdb)"
    c.Expr[FLTT](t)
  }


  private def tpeBases(tpe: c.universe.Type): Seq[c.universe.Type] = {
    tpeBases(tpe, tpe.dealias.resultType)
  }

  private def tpeBases(tpe: c.universe.Type, tpef: c.universe.Type): Seq[c.universe.Type] = {
    val higherBases = tpe.baseClasses
    val parameterizedBases = higherBases
      .filterNot {
        s =>
          val btype = s.asType.toType
          btype =:= any.tpe || btype =:= obj.tpe || btype.erasure =:= tpe.erasure
      }
      .map(s => tpef.baseType(s))

    val hollowBases = higherBases.map(s => s.asType.toType)

    val allbases = parameterizedBases ++ hollowBases
    allbases
  }

  private val any: c.WeakTypeTag[Any] = c.weakTypeTag[Any]
  private val obj: c.WeakTypeTag[Object] = c.weakTypeTag[Object]
  private val nothing: c.WeakTypeTag[Nothing] = c.weakTypeTag[Nothing]

  private def makeRef(tpe: c.universe.Type, path: Set[Type], terminalNames: Map[String, LambdaParameter]): AbstractReference = {

    def makeRef(tpe: Type, stop: Map[String, LambdaParameter] = Map.empty): AbstractReference = {
      LightTypeTagImpl.this.makeRef(tpe, path + tpe, terminalNames ++ stop)
    }

    def makeBoundaries(b: TypeBoundsApi): Boundaries = {
      if ((b.lo =:= nothing.tpe && b.hi =:= any.tpe) || (path.contains(b.lo) || path.contains(b.hi))) {
        Boundaries.Empty
      } else {
        Boundaries.Defined(makeRef(b.lo), makeRef(b.hi))
      }
    }

    def makeKind(kt: c.universe.Type): AbstractKind = {
      val ts = kt.dealias.resultType.typeSymbol.typeSignature

      ts match {
        case b: TypeBoundsApi =>
          val boundaries = makeBoundaries(b)
          val variance = toVariance(kt)

          Hole(boundaries, variance)
        case PolyType(params, b: TypeBoundsApi) =>
          val boundaries = makeBoundaries(b)
          val paramsAsTypes = params.map(_.asType.toType)
          val variance = toVariance(kt)

          Kind(paramsAsTypes.map(makeKind), boundaries, variance)
        case PolyType(params, _) =>
          val paramsAsTypes = params.map(_.asType.toType)
          val variance = toVariance(kt)

          Kind(paramsAsTypes.map(makeKind), Boundaries.Empty, variance)

      }
    }

    def makeLambda(t: Type): AbstractReference = {
      val asPoly = t.etaExpand
      val result = asPoly.resultType.dealias
      val lamParams = t.typeParams.zipWithIndex.map {
        case (p, idx) =>
          p.fullName -> LambdaParameter(idx.toString, idx, makeKind(p.asType.toType), toVariance(p.asType))
      }
      val reference = makeRef(result, lamParams.toMap)
      Lambda(lamParams.map(_._2), reference)
    }

    def unpack(t: c.universe.Type, rules: Map[String, LambdaParameter]): AppliedReference = {
      val tpef = t.dealias.resultType
      val typeSymbol = tpef.typeSymbol

//      val renames = rules.map {
//        r =>
//          r.name -> r.idx
//      }.toMap

      tpef.typeArgs match {
        case Nil =>

          rules.get(typeSymbol.fullName) match {
            case Some(value) =>
              NameReference(value.name)

            case None =>
              NameReference(typeSymbol.fullName)

          }

        case args =>
          val params = args.zip(t.typeConstructor.typeParams).map {
            case (a, pa) =>
              TypeParam(makeRef(a), makeKind(pa.asType.toType), toVariance(pa.asType))
          }
          FullReference(typeSymbol.fullName, params)
      }
    }


    val out = tpe match {
      case _: PolyTypeApi =>
        makeLambda(tpe)
      case p if p.takesTypeArgs =>
        terminalNames.get(p.typeSymbol.fullName) match {
          case Some(value) =>
            unpack(p, Map(p.typeSymbol.fullName -> value))

          case None =>
            makeLambda(p)

        }
//        if (!terminalNames.map(_.name).contains(p.typeSymbol.fullName)) {
//        } else {
//        }
      case c =>
        unpack(c, Set.empty)
    }

    out
  }

  private def toVariance(tpe: c.universe.Type): Variance = {
    val typeSymbolTpe = tpe.typeSymbol.asType
    toVariance(typeSymbolTpe)
  }

  private def toVariance(tpes: TypeSymbol): Variance = {
    if (tpes.isCovariant) {
      Variance.Covariant
    } else if (tpes.isContravariant) {
      Variance.Contravariant
    } else {
      Variance.Invariant
    }
  }

  private def allTypeReferences(tpe: c.universe.Type): Set[c.Type] = {
    val inh = mutable.HashSet[c.Type]()
    extract(tpe, inh)
    inh.toSet
  }

  private def extract(tpe: c.universe.Type, inh: mutable.HashSet[c.Type]): Unit = {
    inh ++= tpeBases(tpe)
    tpe.typeArgs.filterNot(inh.contains).foreach {
      a =>
        extract(a, inh)
    }
  }
}
