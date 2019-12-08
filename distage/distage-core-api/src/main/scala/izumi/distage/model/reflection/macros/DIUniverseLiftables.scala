package izumi.distage.model.reflection.macros

import izumi.distage.model.reflection.universe._

class DIUniverseLiftables[D <: StaticDIUniverse](val u: D) {

  import u._
  import u.u._

  protected[this] val runtimeDIUniverse: Tree = q"_root_.izumi.distage.model.reflection.universe.RuntimeDIUniverse"

  implicit val liftableSafeType: Liftable[SafeType] = {
    value =>
      value.use {
        tpe =>
          q"{ $runtimeDIUniverse.SafeType.get[${Liftable.liftType(tpe)}] }"
      }
  }

  // DIKey

  protected[this] implicit val liftableTypeKey: Liftable[DIKey.TypeKey] = {
    case DIKey.TypeKey(symbol) => q"""
    { new $runtimeDIUniverse.DIKey.TypeKey($symbol) }
      """
  }

  protected[this] implicit val liftableIdKey: Liftable[DIKey.IdKey[_]] = {
    case idKey: DIKey.IdKey[_] =>
      val lifted = idKey.idContract.liftable(idKey.id)
      q"""{ new $runtimeDIUniverse.DIKey.IdKey(${idKey.tpe}, $lifted) }"""
  }

  protected[this] implicit val liftableBasicDIKey: Liftable[DIKey.BasicKey] = {
    Liftable[DIKey.BasicKey] {
      case t: DIKey.TypeKey => q"$t"
      case i: DIKey.IdKey[_] => q"${liftableIdKey(i)}"
    }
  }

  // SymbolInfo

  // currently only function parameter symbols are spliced by this
  // So, `liftableSafeType` is fine and will work, since parameter
  // types must all be resolved anyway - they cannot contain polymorphic
  // components, unlike general method symbols (info for which we don't generate).
  protected[this] implicit val liftableSymbolInfo: Liftable[SymbolInfo] = {
    info =>
      q"""{ $runtimeDIUniverse.SymbolInfo.Static(
      name = ${info.name},
      finalResultType = ${liftableSafeType(info.finalResultType)},
      isByName = ${info.isByName},
      wasGeneric = ${info.wasGeneric}
      ) }"""
  }

  // Associations

  implicit val liftableParameter: Liftable[Association.Parameter] = {
    case Association.Parameter(symbol, key) =>
      q"new $runtimeDIUniverse.Association.Parameter($symbol, $key)"
  }

}

object DIUniverseLiftables {
  def apply(u: StaticDIUniverse): DIUniverseLiftables[u.type] = new DIUniverseLiftables[u.type](u)
}
