package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.{ReflectionUtil, SingletonUniverse}
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.AbstractKind.{Hole, Kind, Proper}
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag._

import scala.collection.mutable
import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

object LTT {
  implicit def apply[T]: FLTT = macro LightTypeTagMacro.makeFLTT[T]
}

object `LTT[_]` {

  trait Fake

  implicit def apply[T[_]]: FLTT = macro LightTypeTagMacro.makeFLTT[T[Fake]]
}

object `LTT[+_]` {

  trait Fake

  implicit def apply[T[+ _]]: FLTT = macro LightTypeTagMacro.makeFLTT[T[Fake]]
}

object `LTT[A, _ <: A]` {
  implicit def apply[A, T[_ <: A]]: FLTT = macro LightTypeTagMacro.makeFLTT[T[A]]
}

object `LTT[_[_]]` {

  trait Fake[F[_[_]]]

  implicit def apply[T[_[_]]]: FLTT = macro LightTypeTagMacro.makeFLTT[T[Fake]]
}

object `LTT[_[_[_]]]` {

  trait Fake[F[_[_[_]]]]

  implicit def apply[T[_[_[_]]]]: FLTT = macro LightTypeTagMacro.makeFLTT[T[Fake]]
}

object `LTT[_,_]` {

  trait Fake

  implicit def apply[T[_, _]]: FLTT = macro LightTypeTagMacro.makeFLTT[T[Fake, Fake]]
}

object `LTT[_[_],_[_]]` {

  trait Fake[_[_]]

  implicit def apply[T[_[_], _[_]]]: FLTT = macro LightTypeTagMacro.makeFLTT[T[Fake, Fake]]
}


object `LTT[_[_[_],_[_]]]` {

  trait Fake[K[_], V[_]]

  implicit def apply[T[_[_[_], _[_]]]]: FLTT = macro LightTypeTagMacro.makeFLTT[T[Fake]]
}

final class LightTypeTagMacro(val c: blackbox.Context) extends LTTLiftables {

  import c.universe._

  protected val impl = new LightTypeTagImpl[c.universe.type](c.universe)

  @inline def makeHKTag[ArgStruct: c.WeakTypeTag]: c.Expr[LHKTag[ArgStruct]] = {
    makeHKTagRaw[ArgStruct](weakTypeOf[ArgStruct])
  }

  @inline def makeTag[T: c.WeakTypeTag]: c.Expr[LTag[T]] = {
    val res = makeFLTTImpl(weakTypeOf[T])
    c.Expr[LTag[T]](q"new ${weakTypeOf[LTag[T]]}($res)")
  }

  @inline def makeWeakTag[T: c.WeakTypeTag]: c.Expr[LWeakTag[T]] = {
    val res = makeFLTTImpl(weakTypeOf[T])
    c.Expr[LWeakTag[T]](q"new ${weakTypeOf[LWeakTag[T]]}($res)")
  }

  @inline def makeFLTT[T: c.WeakTypeTag]: c.Expr[FLTT] = {
    makeFLTTImpl(weakTypeOf[T])
  }

  def makeHKTagRaw[ArgStruct](argStruct: Type): c.Expr[LHKTag[ArgStruct]] = {
    def badShapeError(t: TypeApi) = {
      c.abort(c.enclosingPosition, s"Expected type shape RefinedType `{ type Arg[A] = X[A] }` for summoning `LightTagK[X]`, but got $t (raw: ${showRaw(t)} ${t.getClass})")
    }

    argStruct match {
      case r: RefinedTypeApi =>
        r.decl(TypeName("Arg")) match {
          case sym: TypeSymbolApi =>
            val res = makeFLTTImpl(sym.info.typeConstructor)
            // FIXME: `appliedType` doesn't work here for some reason; have to write down the entire name
            c.Expr[LHKTag[ArgStruct]](q"new _root_.com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LHKTag[$argStruct]($res)")
          case _ => badShapeError(r)
        }
      case other => badShapeError(other)
    }
  }

  protected def makeFLTTImpl(tpe: Type): c.Expr[FLTT] = {
    // FIXME: Try to summon Tags from environment for all found type parameters instead of failing immediately [replicate TagMacro]
    // FIXME: makeWeakTag should disable this check

    // FIXME: summon LTag[Nothing] fails
    //    if (tpe.typeSymbol.isParameter) {
    //      c.abort(c.enclosingPosition, s"Can't assemble Light Type Tag for $tpe – it's an abstract type parameter")
    //    }

    c.Expr[FLTT](lifted_FLLT(impl.makeFLTT(tpe)))
  }
}

// FIXME: Object makes this impossible to override ...
object LightTypeTagImpl {
  def makeFLTT(u: Universe)(typeTag: u.Type): FLTT = {
    new LightTypeTagImpl[u.type](u).makeFLTT(typeTag)
  }
}

// FIXME: AnyVal makes this impossible to override ...
final class LightTypeTagImpl[U <: Universe with Singleton](val u: U) extends AnyVal {

  import u._

  @inline private[this] def any: Type = definitions.AnyTpe

  @inline private[this] def obj: Type = definitions.ObjectTpe

  @inline private[this] def nothing: Type = definitions.NothingTpe

  @inline private[this] def ignored: Set[Type] = Set(any, obj, nothing)


  def makeFLTT(tpe: Type): FLTT = {
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
      .toMultimap
      .map {
        case (t, parents) =>
          t -> parents
            .collect {
              case r: AppliedReference =>
                r.asName
            }
            .filterNot(_ == t)
      }


    val basesdb: Map[AbstractReference, Set[AbstractReference]] = Map(
      out -> tpeBases(tpe).map(b => makeRef(b, Set(b), Map.empty)).filterNot(_ == out).toSet
    )

    //import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    // println(s"$tpe (${inhdb.size}):${inhdb.toSeq.niceList()}")
    // println(inhdb.size)
    // println(s"$tpe (${basesdb.size}):${basesdb.toSeq.niceList()}")
    // println(basesdb.size)

    new FLTT(out, () => basesdb, () => inhdb)
  }

  private def allTypeReferences(tpe: Type): Set[Type] = {
    val inh = mutable.HashSet[Type]()
    extract(tpe, inh)
    inh.toSet
  }

  private def extract(tpe: Type, inh: mutable.HashSet[Type]): Unit = {
    inh ++= Seq(tpe, tpe.dealias.resultType)
    tpe.typeArgs.filterNot(inh.contains).foreach {
      a =>
        extract(a, inh)
    }
  }

  private def tpeBases(tpe: Type): Seq[Type] = {
    tpeBases(tpe, tpe.dealias.resultType)
  }

  private def tpeBases(tpe: Type, tpef: Type, withHollow: Boolean = false): Seq[Type] = {
    val higherBases = tpe.baseClasses
    val parameterizedBases = higherBases
      .filterNot {
        s =>
          val btype = s.asType.toType
          //          println(s"${btype} vs ${tpe}")
          //          println(btype.erasure)
          //          println(tpe.erasure)

          ignored.exists(_ =:= btype) || btype =:= tpe /*|| btype.erasure =:= norm(ReflectionUtil.deannotate(tpe)).erasure.asInstanceOf[Type]*/
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

  private def makeRef(tpe: Type, path: Set[Type], terminalNames: Map[String, LambdaParameter], level: Int = 0): AbstractReference = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    val debug = false

    def xprintln(s: => String): Unit = {
      if (debug) {
        println(s.shift(level, "  "))
      }
    }

    def makeRef(tpe: Type, stop: Map[String, LambdaParameter] = Map.empty): AbstractReference = {
      this.makeRef(tpe, path + tpe, terminalNames ++ stop, level + 1)
    }

    def makeBoundaries(b: TypeBoundsApi): Boundaries = {
      if ((b.lo =:= nothing && b.hi =:= any) || (path.contains(b.lo) || path.contains(b.hi))) {
        Boundaries.Empty
      } else {
        Boundaries.Defined(makeRef(b.lo), makeRef(b.hi))
      }
    }

    def makeKind(kt: Type): AbstractKind = {
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

      xprintln(s"working on lambda $t")
      xprintln(s" => Current parameters: $lamParams")
      xprintln(s" => Terminal names: $terminalNames")
      val reference = makeRef(result, lamParams.toMap)
      xprintln(s"   result: $reference")


      Lambda(lamParams.map(_._2), reference)
    }

    def unpack(t: Type, rules: Map[String, LambdaParameter]): AppliedReference = {
      val tpef = t.dealias.resultType
      val typeSymbol = tpef.typeSymbol

      val nameref = rules.get(typeSymbol.fullName) match {
        case Some(value) =>
          NameReference(value.name)

        case None =>
          NameReference(typeSymbol.fullName)
      }

      tpef.typeArgs match {
        case Nil =>
          nameref

        case args =>
          val params = args.zip(t.dealias.typeConstructor.typeParams).map {
            case (a, pa) =>
              TypeParam(makeRef(a), makeKind(pa.asType.toType), toVariance(pa.asType))
          }
          FullReference(nameref.ref, params)
      }
    }

    val out = tpe match {
      case _: PolyTypeApi =>
        makeLambda(tpe)
      case p if p.takesTypeArgs =>

        xprintln(s"typearg type $p, context: $terminalNames")
        if (terminalNames.contains(p.typeSymbol.fullName)) {
          unpack(p, terminalNames)
        } else {
          makeLambda(p)
        }

      case c =>
        xprintln(s"applied type $c, context: $terminalNames")

        unpack(c, terminalNames)
    }

    out
  }

  private def toVariance(tpe: Type): Variance = {
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
