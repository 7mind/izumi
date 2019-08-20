package izumi.fundamentals.reflection

import izumi.fundamentals.reflection.ReflectionUtil._

/** scala-reflect `Type` wrapped to provide a contract-abiding equals-hashCode */
class StableType[U <: SingletonUniverse](private val u: U, val tpe: U#Type) {

  private final val dealiased: U#Type = {
    deannotate(tpe.dealias)
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

        new StableType(u, tpe.pre.asInstanceOf[U#Type]) == new StableType(u, other.pre.asInstanceOf[U#Type]) && tpe.sym.name.toString == other.sym.name.toString
      case _ =>
        false
    }

  override final val hashCode: Int = {
    dealiased.typeSymbol.name.toString.hashCode
  }

  override final def equals(obj: Any): Boolean = {
    obj match {
      case that: StableType[U] @unchecked if u == that.u =>
        dealiased =:= that.dealiased ||
          singletonFreeTermHeuristicEq(dealiased, that.dealiased) ||
          freeTermPrefixTypeSuffixHeuristicEq(_ =:= _, dealiased, that.dealiased)
      case _ =>
        false
    }
  }
}
