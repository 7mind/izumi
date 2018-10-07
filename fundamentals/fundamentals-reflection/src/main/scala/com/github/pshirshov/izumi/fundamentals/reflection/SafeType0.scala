package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.ReflectionUtil._

import scala.reflect.runtime.{universe => ru}

// TODO: hotspots, hashcode on keys is inefficient
class SafeType0[U <: SingletonUniverse](
                                         private val u: U // Needed just for the corner case in TagText."work with odd type prefixes" ._.
                                         , val tpe: U#Type) {

  private final val dealiased: U#Type = {
    deannotate(tpe.dealias)
  }

  override final lazy val toString: String = {
    dealiased.toString
  }

  @inline private[this] final def freeTermPrefixTypeSuffixHeuristicEq(op: (U#Type, U#Type) => Boolean, t: U#Type, that: U#Type): Boolean =
    t -> that match {
      case (tRef: U#TypeRefApi, oRef: U#TypeRefApi) =>
        singletonFreeTermHeuristicEq(tRef.pre, oRef.pre) && (
          tRef.sym.isType && oRef.sym.isType && {
            val t1 = (u: U).internal.typeRef(u.NoType, tRef.sym, tRef.args)
            val t2 = (u: U).internal.typeRef(u.NoType, oRef.sym, oRef.args)

            op(t1, t2)
          }
        || tRef.sym.isTerm && oRef.sym.isTerm && tRef.sym == oRef.sym
      )
      case (tRef: U#SingleTypeApi, oRef: U#SingleTypeApi) =>
        singletonFreeTermHeuristicEq(tRef.pre, oRef.pre) && tRef.sym == oRef.sym
      case _ => false
    }

  private[this] final def singletonFreeTermHeuristicEq(t: U#Type, that: U#Type): Boolean =
    t.asInstanceOf[Any] -> that.asInstanceOf[Any] match {
      case (tpe: scala.reflect.internal.Types#UniqueSingleType, other: scala.reflect.internal.Types#UniqueSingleType)
        if tpe.sym.isFreeTerm && other.sym.isFreeTerm =>

        new SafeType0(u, tpe.pre.asInstanceOf[U#Type]) == new SafeType0(u, other.pre.asInstanceOf[U#Type]) && tpe.sym.name.toString == other.sym.name.toString
      case _ =>
        false
  }

  /**
    * Workaround for Type's hashcode being unstable with sbt fork := false and version of scala other than 2.12.4
    * (two different scala-reflect libraries end up on the classpath leading to undefined hashcode)
    **/
  override final val hashCode: Int = {
    dealiased.typeSymbol.name.toString.hashCode
  }

  // Criteria for heuristic:
  // Is a UniqueSingleType & .sym is FreeTerm
  //  Or the same for .prefix
  // Purpose of heuristic:
  //  unify two different singleton values with the same name and type
  //  and unify their children, even if they are local variables, not static objects.
  //  We have to stoop down to string checks because scalac's `TypeTag` is not being
  //  useful about local variable singleton's – it contains only the name of the variable
  //  and considers it a "FreeTerm", even though it's not!
  override final def equals(obj: Any): Boolean = {
    obj match {
      case that: SafeType0[U] @unchecked =>
        dealiased =:= that.dealiased ||
          singletonFreeTermHeuristicEq(dealiased, that.dealiased) ||
          freeTermPrefixTypeSuffixHeuristicEq(_ =:= _, dealiased, that.dealiased)
      case _ =>
        false
    }
  }

  final def <:<(that: SafeType0[U]): Boolean =
    dealiased <:< that.dealiased || freeTermPrefixTypeSuffixHeuristicEq(_ <:< _, dealiased, that.dealiased)

  final def weak_<:<(that: SafeType0[U]): Boolean =
    (dealiased weak_<:< that.dealiased) || freeTermPrefixTypeSuffixHeuristicEq(_ weak_<:< _, dealiased, that.dealiased)
}

object SafeType0 {

  def apply[U <: SingletonUniverse](u: U, tpe: U#Type): SafeType0[U] = new SafeType0[U](u, tpe)

  def apply(tpe: ru.Type): SafeType0[ru.type] = new SafeType0[ru.type](ru, tpe)

  def get[T: ru.TypeTag]: SafeType0[ru.type] = new SafeType0[ru.type](ru, ru.typeOf[T])
}
