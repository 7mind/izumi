package com.github.pshirshov.izumi.distage.model.definition.reflection

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}

class DIUniverseMacros[D <: StaticDIUniverse](val u: D) {
  import u._
  import u.u._

  @deprecated("should preserve all additional annotations too", "")
  def annotationsFromDIKey(key: u.DIKey): u.u.Modifiers = {
    key match {
      case idKey: u.DIKey.IdKey[_] =>
        import idKey._
        val ann = q"""new ${typeOf[Id]}(${id.toString})"""
        Modifiers.apply(NoFlags, typeNames.EMPTY, List(ann))
      case _ =>
        Modifiers()
    }
  }

  implicit val liftableRuntimeUniverse: Liftable[RuntimeDIUniverse.type] =
    { _: RuntimeDIUniverse.type => q"${symbolOf[RuntimeDIUniverse.type].asClass.module}" }

  implicit val liftableSafeType: Liftable[SafeType] =
    value => q"{ $RuntimeDIUniverse.SafeType.getWeak[${Liftable.liftType(value.tpe)}] }"

  // DIKey

  implicit val liftableTypeKey: Liftable[DIKey.TypeKey] = {
    case DIKey.TypeKey(symbol) => q"""
    { new $RuntimeDIUniverse.DIKey.TypeKey($symbol) }
      """
  }

  implicit val liftableIdKey: Liftable[DIKey.IdKey[_]] = {
    case idKey: DIKey.IdKey[_] =>
      import idKey._
      val lift = idContract.asInstanceOf[IdContractImpl[Any]].liftable
      q"""{ new $RuntimeDIUniverse.DIKey.IdKey($symbol, ${lift(id)}) }"""
  }

  implicit val liftableDIKey: Liftable[DIKey] = {
    Liftable[DIKey] {
      case t: DIKey.TypeKey => q"$t"
      case i: DIKey.IdKey[_] => q"${liftableIdKey(i)}"
      case p: DIKey.ProxyElementKey => q"$p"
      case s: DIKey.SetElementKey => q"$s"
    }
  }

  implicit val liftableProxyElementKey: Liftable[DIKey.ProxyElementKey] = {
    case DIKey.ProxyElementKey(proxied, symbol) => q"""
    { new $RuntimeDIUniverse.DIKey.ProxyElementKey(${liftableDIKey(proxied)}, $symbol) }
      """
  }

  implicit val liftableSetElementKey: Liftable[DIKey.SetElementKey] = {
    case DIKey.SetElementKey(set, index, symbol) => q"""
    { new $RuntimeDIUniverse.DIKey.SetElementKey(${liftableDIKey(set)}, $index, $symbol) }
      """
  }

}

object DIUniverseMacros {
  def apply(u: StaticDIUniverse): DIUniverseMacros[u.type] = new DIUniverseMacros[u.type](u)
}
