package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.AbstractKind.{Hole, Kind, Proper}
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag._

import scala.collection.mutable
import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.macros.blackbox


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

object `LTT[_[_[_]]]` {

  trait Fake[F[_[_[_]]]]

  implicit def apply[T[_[_[_]]]]: FLTT = macro LightTypeTagImpl.makeTag[T[Fake]]
}

object `LTT[_,_]` {

  trait Fake

  implicit def apply[T[_, _]]: FLTT = macro LightTypeTagImpl.makeTag[T[Fake, Fake]]
}

object `LTT[_[_],_[_]]` {

  trait Fake[_[_]]

  implicit def apply[T[_[_],_[_]]]: FLTT = macro LightTypeTagImpl.makeTag[T[Fake, Fake]]
}


object `LTT[_[_[_],_[_]]]` {

  trait Fake[K[_],V[_]]

  implicit def apply[T[_[_[_],_[_]]]]: FLTT = macro LightTypeTagImpl.makeTag[T[Fake]]
}

protected[macrortti] final class LightTypeTagImpl(val c: blackbox.Context) extends LTTLiftables {

  import c.universe._


  def makeTag[T: c.WeakTypeTag]: c.Expr[FLTT] = {
    import c._
    val wtt = implicitly[WeakTypeTag[T]]
    val tpe = wtt.tpe

    val out = makeRef(tpe, Set(tpe), Map.empty)
    val inh = allTypeReferences(tpe)

    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val inhdb = inh
      .flatMap {
        i =>
          val targetNameRef = i.dealias.resultType.typeSymbol.fullName
          val srcname = i match {
            case a: TypeRefApi =>
              val srcname = a.sym.fullName
              if (srcname != targetNameRef) {
                Seq((NameReference(srcname), NameReference(targetNameRef)))
              } else {
                Seq.empty
              }

            case _ =>
              Seq.empty
          }


          val allbases = tpeBases(i)
          srcname ++ allbases.map {
            b =>
              (NameReference(targetNameRef), makeRef(b, Set(b), Map.empty))
          }
      }
      .toMultimap.mapValues {
      parents =>
        parents.collect {
          case r: AppliedReference =>
            r.asName
        }
    }


    val basesdb: Map[AbstractReference, Set[AbstractReference]] = Map(
      out -> tpeBases(tpe).map(b => makeRef(b, Set(b), Map.empty)).toSet
    )

    //import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    // println(s"$tpe (${inhdb.size}):${inhdb.toSeq.niceList()}")
    // println(inhdb.size)
    // println(s"$tpe (${basesdb.size}):${basesdb.toSeq.niceList()}")
    // println(basesdb.size)

    val t = q"new FLTT($out, () => $basesdb, () => $inhdb)"
    c.Expr[FLTT](t)
  }

  private def allTypeReferences(tpe: c.universe.Type): Set[c.Type] = {
    val inh = mutable.HashSet[c.Type]()
    extract(tpe, inh)
    inh.toSet
  }

  private def extract(tpe: c.universe.Type, inh: mutable.HashSet[c.Type]): Unit = {
    inh ++= Seq(tpe, tpe.dealias.resultType)
    tpe.typeArgs.filterNot(inh.contains).foreach {
      a =>
        extract(a, inh)
    }
  }

  private def tpeBases(tpe: c.universe.Type): Seq[c.universe.Type] = {
    tpeBases(tpe, tpe.dealias.resultType)
  }

  private def tpeBases(tpe: c.universe.Type, tpef: c.universe.Type, withHollow: Boolean = false): Seq[c.universe.Type] = {
    val higherBases = tpe.baseClasses
    val parameterizedBases = higherBases
      .filterNot {
        s =>
          val btype = s.asType.toType
          btype =:= any.tpe || btype =:= obj.tpe || btype.erasure =:= tpe.erasure
      }
      .map(s => tpef.baseType(s))

    val hollowBases = if (withHollow) {
      higherBases.map(s => s.asType.toType)
    } else {
      Seq.empty
    }

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
      val variance = toVariance(kt)

      if (ts.takesTypeArgs) {
        ts match {
          case b: TypeBoundsApi =>
            val boundaries = makeBoundaries(b)
            Hole(boundaries, variance)

          case PolyType(params, b: TypeBoundsApi) =>
            val boundaries = makeBoundaries(b)
            val paramsAsTypes = params.map(_.asType.toType)
            Kind(paramsAsTypes.map(makeKind), boundaries, variance)

          case PolyType(params, _) =>
            val paramsAsTypes = params.map(_.asType.toType)
            Kind(paramsAsTypes.map(makeKind), Boundaries.Empty, variance)
        }
      } else {
        Proper
      }
    }

    def makeLambda(t: Type): AbstractReference = {
      val asPoly = t.etaExpand
      val result = asPoly.resultType.dealias
      val lamParams = t.typeParams.zipWithIndex.map {
        case (p, idx) =>
          p.fullName -> LambdaParameter(idx.toString, makeKind(p.asType.toType))
      }
      val reference = makeRef(result, lamParams.toMap)
      Lambda(lamParams.map(_._2), reference)
    }

    def unpack(t: c.universe.Type, rules: Map[String, LambdaParameter]): AppliedReference = {
      val tpef = t.dealias.resultType
      val typeSymbol = tpef.typeSymbol

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
        if (terminalNames.contains(p.typeSymbol.fullName)) {
          unpack(p, terminalNames)
        } else {
          makeLambda(p)
        }

      case c =>
        unpack(c, terminalNames)
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
}
