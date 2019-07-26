package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.LightTypeTag.AbstractKind.{Hole, Kind}
import com.github.pshirshov.izumi.fundamentals.reflection.LightTypeTag._
import com.github.pshirshov.izumi.fundamentals.reflection.LightTypeTag.TypeParameter._
//import com.github.pshirshov.izumi.fundamentals.reflection.LightTypeTag.TypeParameter.AbstractTypeParameter._

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

    case class Defined(bottom: LightTypeTag, top: LightTypeTag) extends Boundaries  {
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
        val p= parameters.mkString(", ")
        s"${variance}_[$p]"
      }
    }

  }

}

class TypeTagExampleImpl(val c: blackbox.Context) {

  import c.universe._

  def makeTag[T: c.WeakTypeTag, TT: c.WeakTypeTag]: c.Expr[FLTT] = {
    import c._
    val wtt = implicitly[WeakTypeTag[T]]
    val tpe = wtt.tpe

    val w = implicitly[WeakTypeTag[TT]]

    val out = makeRef(tpe, Set(tpe))
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
    val t = q"new FLTT(new ${w.tpe}($out), () => ???)"
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

  private def makeRef(tpe: c.universe.Type, path: Set[Type]): AbstractReference = {

    def makeRef(tpe: Type): AbstractReference = {
      TypeTagExampleImpl.this.makeRef(tpe, path + tpe)
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
          //          assert(params.map(_.asType.typeSignature).forall(isUnbound))

          val paramsAsTypes = params.map(_.asType.toType)
          val variance = toVariance(kt)

          Kind(paramsAsTypes.map(makeKind), boundaries, variance)
        case PolyType(params, _) =>
          val paramsAsTypes = params.map(_.asType.toType)
          val variance = toVariance(kt)

          Kind(paramsAsTypes.map(makeKind), Boundaries.Empty, variance)

      }
    }


//    println(s"begin")
//    diag(tpe)
//    println("end")

    def makeLambda(t: Type, scope: Set[LambdaParameter]): AbstractReference = {
      val asPoly = t.etaExpand
      val result = asPoly.resultType.dealias
//      println(s"  Polytype: $t := (${t.typeParams}) => $result; ${t.typeParams.map(p => toVariance(p.asType))}; ${t.typeParams.map(p => makeKind(p.asType.toType))}")
      val lamParams = t.typeParams.map {
        p =>
          LambdaParameter(p.fullName, toVariance(p.asType))
      }


      val reference = if (scope.map(_.name).contains(result.typeSymbol.fullName)) {
        unpack0(result)
      } else {
        makeRef(result)
      } /*match {
        case Lambda(input, output, kind) =>
          output
        case reference: AppliedReference =>
          reference
      }*/
//      println((t, asPoly, result, reference, t.typeParams, t.typeParams.map(_.name), t.typeParams.forall(_.name.encodedName == "_")))

//      val sub = if (t.typeParams.forall(_.name.toString.startsWith("_"))) {
//        reference
//      } else {
//
//      }
////      println(sub)
//      sub
      Lambda(lamParams, reference, makeKind(t))

    }

     def unpack(t: c.universe.Type): AppliedReference = {
       //assert(t.typeParams.isEmpty)
       val tpef = t.dealias.resultType
       val typeSymbol = tpef.typeSymbol

//       diag(tpef)

       tpef.typeArgs match {
         case Nil =>
           NameReference(typeSymbol.fullName)

         case args =>
           val params = args.map {
             a =>
               Ref(makeRef(a), toVariance(a))
           }
           FullReference(typeSymbol.fullName, params)
       }
     }

    def unpack0(t: c.universe.Type): AppliedReference = {
      val tpef = t.dealias.resultType
      val typeSymbol = tpef.typeSymbol

      tpef.typeArgs match {
        case Nil =>
          NameReference(typeSymbol.fullName)

        case args =>
          val params = args.map {
            a =>
              Ref(unpack(a), toVariance(a))
          }
          FullReference(typeSymbol.fullName, params)
      }
    }

      val out = tpe match {
      case _: PolyTypeApi =>
        //println(("pta", tpe))

        makeLambda(tpe)
      case p if p.takesTypeArgs =>
        //println(("tta", p))
        makeLambda(p)
//
//        val result = p.etaExpand.resultType.dealias
//        println(s"  W/args: $p := (${p.typeParams}) => $result; ${p.typeParams.map(p => toVariance(p.asType))}; ${p.typeParams.map(p => makeKind(p.asType.toType))}")
      case c =>
          unpack(c)
    }

    //    println(tpef)
    //    println(tpef.typeArgs)
    //    println(tpef.typeSymbol.typeSignature.getClass)
    //    println((tpef.typeSymbol.isAbstract, tpef.typeSymbol.isSynthetic, tpef.typeSymbol.isImplementationArtifact, tpef.typeSymbol.isClass, tpef.typeSymbol.asClass.decls))
    //
    //    if (tpef.typeArgs.nonEmpty) {
    //      println(tpef.typeArgs.head.typeSymbol.typeSignature)
    //    }



    out
//    val typeSymbol = tpef.typeSymbol
//
//    val typeSymbolTpe = typeSymbol.asType
//    val variance = toVariance(tpef)
//    val out = if (tpef.takesTypeArgs || isKindProjectorLambda(tpef)) {
//      val args = if (tpef.typeArgs.isEmpty) {
//        tpef.typeParams.map(_.asType.toType).map {
//          t =>
//            val ts = t.dealias.resultType.typeSymbol
//            ts.typeSignature match {
//              case _: TypeBoundsApi =>
//                makeKind(t)
//              case PolyType(_, _: TypeBoundsApi) =>
//                makeKind(t)
//              case _ =>
//                Ref(makeRef(t), variance)
//            }
//
//        }
//      } else {
//        tpef.typeArgs.map {
//          t =>
//            Ref(makeRef(t), variance)
//
//        }
//      }
//
//      val params = args
//
//      FullReference(typeSymbol.fullName, params)
//    } else {
//
//    }
//    out
  }

  //  private def isUnbound(tpef: c.universe.Type): Boolean = {
  //    tpef match {
  //      case _: TypeBoundsApi =>
  //        true
  //      case PolyType(_, _: TypeBoundsApi) =>
  //        true
  //      case _ =>
  //        false
  //    }
  //  }



  private def isKindProjectorLambda(tpef: c.universe.Type) = {
    tpef.typeSymbol.typeSignature.isInstanceOf[PolyTypeApi] && tpef.typeArgs.exists(p => p.typeSymbol.isParameter)
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
    case LightTypeTag.AbstractKind.Hole(b, v) =>
      q"$LightTypeTag.AbstractKind.Hole($b, $v)"

    case LightTypeTag.AbstractKind.Kind(parameters, b, v) =>
      q"$LightTypeTag.AbstractKind.Kind($parameters, $b, $v)"

  }

  protected implicit def lifted_AppliedReference: Liftable[AppliedReference] = Liftable[AppliedReference] {
    case NameReference(ref) =>
      q"$LightTypeTag.NameReference($ref)"
    case FullReference(ref, parameters) =>
      q"$LightTypeTag.FullReference($ref, $parameters)"
  }

  protected implicit def lifted_TypeParameter: Liftable[TypeParameter] = Liftable[TypeParameter] {
//    case a: LightTypeTag.TypeParameter.AbstractTypeParameter =>
//      implicitly[Liftable[AbstractTypeParameter]].apply(a)
    case LightTypeTag.TypeParameter.Ref(r, v) =>
      q"$LightTypeTag.TypeParameter.Ref($r, $v)"
  }

  protected implicit def lifted_LambdaParameter: Liftable[LambdaParameter] = Liftable[LambdaParameter] {
    p =>
      q"$LightTypeTag.LambdaParameter(${p.name}, ${p.variance})"
  }
  protected implicit def lifted_AbstractReference: Liftable[AbstractReference] = Liftable[AbstractReference] {
    case Lambda(in, out, k) =>
      q"$LightTypeTag.Lambda($in, $out, $k)"
    case a: AppliedReference =>
      implicitly[Liftable[AppliedReference]].apply(a)


  }

  protected implicit def lifted_LightTypeTag: Liftable[LightTypeTag] = Liftable[LightTypeTag] {
    case r: AbstractReference =>
      implicitly[Liftable[AbstractReference]].apply(r)
    case k: TypeParameter =>
      implicitly[Liftable[TypeParameter]].apply(k)
  }

  //    protected implicit def lifted_ShortReference: Liftable[ShortReference] = Liftable[ShortReference] {
  //      case ShortNameReference(ref) =>
  //        q"$LightTypeTag.ShortNameReference($ref)"
  //      case ShortFullReference(ref, parameters) =>
  //        q"$LightTypeTag.ShortFullReference($ref, $parameters)"
  //    }
}
