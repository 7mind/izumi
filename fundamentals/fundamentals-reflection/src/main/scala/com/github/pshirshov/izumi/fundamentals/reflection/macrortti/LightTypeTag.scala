package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.AbstractKind.{Hole, Kind}
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.TypeParameter._
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag._
//import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.TypeParameter.AbstractTypeParameter._

import scala.collection.mutable
import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.macros.blackbox

class FLTT(val t: LightTypeTag, db: () => Map[AbstractReference, Set[AbstractReference]]) {
  lazy val idb: Map[AbstractReference, Set[AbstractReference]] = db()

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
        LightTypeTag.appLam(l, parameters)
      case _ =>
        throw new IllegalArgumentException(s"$this is not a type lambda, it cannot be parameterized with $o")
    }
  }
}

object LightTypeTag {

  def replace(ref: AbstractReference, xparameters: Map[String, AbstractReference]): AbstractReference = {
    ref match {
      case l: Lambda =>
        l
      case n@NameReference(ref) =>
        xparameters.get(ref) match {
          case Some(value) =>
            value
          case None =>
            n
        }

      case FullReference(ref, parameters) =>
        val p = parameters.map {
          case Ref(ref, variance) =>
            Ref(replace(ref, xparameters), variance)
        }
        FullReference(ref, p)
    }
  }

  def appLam(lambda: Lambda, parameters: Map[String, AbstractReference]): AbstractReference = {
    val newParams = lambda.input.filterNot(p => parameters.contains(p.name))
    val replaced = replace(lambda.output, parameters)

    if (newParams.isEmpty) {
      replaced
    } else {
      Lambda(newParams, replaced, lambda.kind)
    }
  }

  sealed trait AbstractReference extends LightTypeTag

  case class Lambda(input: List[LambdaParameter], output: AbstractReference, kind: AbstractKind) extends AbstractReference {
    override def toString: String = s"λ${input.mkString("(", ",", ")")} → $output"
  }

  case class LambdaParameter(name: String, variance: Variance) {
    override def toString: String = s" %($variance${name.split('.').last}) "
  }

  sealed trait AppliedReference extends AbstractReference

  case class NameReference(ref: String) extends AppliedReference {
    override def toString: String = ref.split('.').last
  }

  case class FullReference(ref: String, parameters: List[TypeParameter]) extends AppliedReference {
    override def toString: String = s"${ref.split('.').last}${parameters.mkString("[", ",", "]")}"
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

  sealed trait TypeParameter {
    def variance: Variance
  }

  object TypeParameter {

    case class Ref(ref: AbstractReference, variance: Variance) extends TypeParameter {
      override def toString: String = s" $variance$ref "
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




final class LightTypeTagImpl(val c: blackbox.Context) extends LTTLiftables{
  import c.universe._

  def makeTag[T: c.WeakTypeTag]: c.Expr[FLTT] = {
    import c._
    val wtt = implicitly[WeakTypeTag[T]]
    val tpe = wtt.tpe

    //val w = implicitly[WeakTypeTag[TT]]

    val out = makeRef(tpe, Set(tpe), Set.empty)
    //
    //    val inh = mutable.HashSet[c.Type]()
    //    extract(tpe, inh)
    //
    //    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    //    val inhdb = inh.flatMap {
    //      i =>
    //        val iref = makeRef(i, Set(i))
    //        val allbases = tpeBases(i)
    //        val out = allbases.map(b => makeRef(b, Set(b)))
    //          .map(b => iref -> b)
    //          .collect {
    //            case (i: AbstractReference, b: AbstractReference) =>
    //              i -> b
    //          }
    //
    //        out
    //
    //    }.toMultimap

    //    println(inhdb.size)
    val t = q"new FLTT($out, () => ???)"
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

  //  private def diag(tpef: c.universe.Type) = {
  //    println(s"  $tpef")
  ////    println(s"  has args: ${tpef.takesTypeArgs}")
  ////    println(s"  is lambda: ${isKindProjectorLambda(tpef)}")
  //    println(s"  params: ${tpef.typeParams}")
  //    println(s"  args: ${tpef.typeArgs}")
  ////    println(s"  eta: ${tpef.etaExpand}")
  ////    println(s"  eta: ${tpef.etaExpand.resultType.dealias}")
  ////    if (tpef.isInstanceOf[PolyTypeApi]) {
  ////      println("POLY")
  ////    }
  ////    if (tpef.typeSymbol.typeSignature.isInstanceOf[PolyTypeApi]) {
  ////      val a = tpef.typeSymbol.typeSignature.asInstanceOf[PolyTypeApi]
  ////      println(s"  isPoly, params: ${a.typeParams}")
  ////    }
  ////    println((tpef.getClass.getInterfaces.toList, tpef.getClass.getSuperclass))
  //  }

  private def makeRef(tpe: c.universe.Type, path: Set[Type], terminalNames: Set[String]): AbstractReference = {

    def makeRef(tpe: Type, stop: Set[LambdaParameter] = Set.empty): AbstractReference = {
      LightTypeTagImpl.this.makeRef(tpe, path + tpe, terminalNames ++ stop.map(_.name))
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
      val lamParams = t.typeParams.map {
        p =>
          LambdaParameter(p.fullName, toVariance(p.asType))
      }
      val reference = makeRef(result, lamParams.toSet)
      Lambda(lamParams, reference, makeKind(t))
    }

    def unpack(t: c.universe.Type): AppliedReference = {
      val tpef = t.dealias.resultType
      val typeSymbol = tpef.typeSymbol

      tpef.typeArgs match {
        case Nil =>
          NameReference(typeSymbol.fullName)

        case args =>
          val params = args.zip(t.typeConstructor.typeParams).map {
            case (a, pa) =>
              Ref(makeRef(a), toVariance(pa.asType))
          }
          FullReference(typeSymbol.fullName, params)
      }
    }


    val out = tpe match {
      case _: PolyTypeApi =>
        makeLambda(tpe)
      case p if p.takesTypeArgs && !terminalNames.contains(p.typeSymbol.fullName) =>
        makeLambda(p)
      case c =>
        unpack(c)
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
}
