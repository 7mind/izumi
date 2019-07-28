package com.github.pshirshov.izumi.fundamentals.reflection

//import com.github.pshirshov.izumi.fundamentals.reflection.ReflectionUtil._
import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.{FLTT, LTag, LightTypeTagImpl}

import scala.reflect.runtime.{universe => ru}

// TODO: hotspots, hashcode on keys is inefficient
class SafeType0[U <: SingletonUniverse] protected (
  private val u: U, // Needed just for the corner case in TagTest."work with odd type prefixes" ._.
  val tpe: U#Type,
  protected[reflection] val fullLightTypeTag: FLTT,
) {

  override final val hashCode: Int = {
//    if (gotLTag)
      fullLightTypeTag.hashCode()
//    else
//      ScalaReflect_hashCode
  }

  override final lazy val toString: String = {
//    if (gotLTag)
      fullLightTypeTag.toString
//    else
//      ScalaReflect_toString
  }

  override final def equals(obj: Any): Boolean = {
    obj match {
      case that: SafeType0[U] @unchecked =>
//        if (gotLTag && that.gotLTag)
          fullLightTypeTag == that.fullLightTypeTag
//        else {
//          println(s"Hit NO LIGHT TYPE TAG on ==($this, $that) [thisLT: ${this.gotLTag}, thatLT: ${that.gotLTag}]")
//          ScalaReflect_equals(obj)
//        }
      case _ =>
        false
    }
  }

  // <:< is not thread-safe and upstream refuses to fix that.
  // https://github.com/scala/bug/issues/10766
  final def <:<(that: SafeType0[U]): Boolean = {
//    if (gotLTag && that.gotLTag)
      fullLightTypeTag <:< that.fullLightTypeTag
//    else {
//      println(s"Hit NO LIGHT TYPE TAG on <:<($this, $that) [thisLT: ${this.gotLTag}, thatLT: ${that.gotLTag}]")
//      ScalaReflect_<:<(that)
//    }
  }

  @deprecated("Weak conformance is useless for DI; weakly conformed numbers are not actually assignable in runtime", "0.9.0")
  final def weak_<:<(that: SafeType0[U]): Boolean = {
//    if (gotLTag && that.gotLTag)
      fullLightTypeTag <:< that.fullLightTypeTag
//    else {
//      println(s"Hit NO LIGHT TYPE TAG on weak_<:<($this, $that) [thisLT: ${this.gotLTag}, thatLT: ${that.gotLTag}]")
//      ScalaReflect_weak_<:<(that)
//    }
  }

//  private final def gotLTag: Boolean = fullLightTypeTag ne null

  // FIXME TOD  __ OLD __

//  private final lazy val dealiased: U#Type = SafeType0.synchronized {
//    deannotate(tpe.dealias)
//  }
//
//  private[this] final def ScalaReflect_hashCode: Int = SafeType0.synchronized {
//    dealiased.typeSymbol.name.toString.hashCode
//  }
//
//  private[this] final def ScalaReflect_toString: String = {
//    dealiased.toString
//  }
//
//  @inline private[this] final def freeTermPrefixTypeSuffixHeuristicEq(op: (U#Type, U#Type) => Boolean, t: U#Type, that: U#Type): Boolean =
//    t -> that match {
//      case (tRef: U#TypeRefApi, oRef: U#TypeRefApi) =>
//        singletonFreeTermHeuristicEq(tRef.pre, oRef.pre) && (
//          tRef.sym.isType && oRef.sym.isType && {
//            val t1 = (u: U).internal.typeRef(u.NoType, tRef.sym, tRef.args)
//            val t2 = (u: U).internal.typeRef(u.NoType, oRef.sym, oRef.args)
//
//            op(t1, t2)
//          }
//        || tRef.sym.isTerm && oRef.sym.isTerm && tRef.sym == oRef.sym
//      )
//      case (tRef: U#SingleTypeApi, oRef: U#SingleTypeApi) =>
//        singletonFreeTermHeuristicEq(tRef.pre, oRef.pre) && tRef.sym == oRef.sym
//      case _ => false
//    }
//
//  private[this] final def singletonFreeTermHeuristicEq(t: U#Type, that: U#Type): Boolean =
//    t.asInstanceOf[Any] -> that.asInstanceOf[Any] match {
//      case (tpe: scala.reflect.internal.Types#UniqueSingleType, other: scala.reflect.internal.Types#UniqueSingleType)
//        if tpe.sym.isFreeTerm && other.sym.isFreeTerm =>
//
//        new SafeType0(u, tpe.pre.asInstanceOf[U#Type], null) == new SafeType0(u, other.pre.asInstanceOf[U#Type], null) && tpe.sym.name.toString == other.sym.name.toString
//      case _ =>
//        false
//  }
//
//  // Criteria for heuristic:
//  // Is a UniqueSingleType & .sym is FreeTerm
//  //  Or the same for .prefix
//  // Purpose of heuristic:
//  //  unify two different singleton values with the same name and type
//  //  and unify their children, even if they are local variables, not static objects.
//  //  We have to stoop down to string checks because scalac's `TypeTag` is not being
//  //  useful about local variable singleton's – it contains only the name of the variable
//  //  and considers it a "FreeTerm", even though it's not!
//  private[this] final def ScalaReflect_equals(obj: Any): Boolean = {
//    obj match {
//      case that: SafeType0[U] @unchecked =>
//        (tpe eq that.tpe) ||
//          // Synchronizing =:= too just in case...
//          // https://github.com/scala/bug/issues/10766
//          SafeType0.synchronized {
//            dealiased =:= that.dealiased ||
//              singletonFreeTermHeuristicEq(dealiased, that.dealiased) ||
//              freeTermPrefixTypeSuffixHeuristicEq(_ =:= _, dealiased, that.dealiased)
//          }
//      case _ =>
//        false
//    }
//  }
//
//  // <:< is not thread-safe and upstream refuses to fix that.
//  // https://github.com/scala/bug/issues/10766
//  private[this] final def ScalaReflect_<:<(that: SafeType0[U]): Boolean =
//    SafeType0.synchronized {
//      dealiased <:< that.dealiased || freeTermPrefixTypeSuffixHeuristicEq(_ <:< _, dealiased, that.dealiased)
//    }
//
//  private[this] final def ScalaReflect_weak_<:<(that: SafeType0[U]): Boolean =
//    SafeType0.synchronized {
//      (dealiased weak_<:< that.dealiased) || freeTermPrefixTypeSuffixHeuristicEq(_ weak_<:< _, dealiased, that.dealiased)
//    }
}

object SafeType0 {
  @deprecated("constructing SafeType from a runtime type tag", "0.9.0")
  @deprecated("constructing SafeType from a runtime type tag", "0.9.0")
  def apply(tpe: ru.Type): SafeType0[ru.type] = new SafeType0[ru.type](ru, tpe, LightTypeTagImpl.makeFLTT(ru)(tpe))

  def get[T: ru.TypeTag: LTag]: SafeType0[ru.type] = new SafeType0[ru.type](ru, ru.typeOf[T], LTag[T].fullLightTypeTag)
}
