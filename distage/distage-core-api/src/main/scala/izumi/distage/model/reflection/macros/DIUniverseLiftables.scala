package izumi.distage.model.reflection.macros

import izumi.distage.model.reflection.universe._

abstract class DIUniverseLiftables[D <: StaticDIUniverse](val u: D) {

  import u._
  import u.u._

  val runtimeDIUniverse: Tree = q"_root_.izumi.distage.model.reflection.universe.RuntimeDIUniverse"

  implicit def liftableSafeType: Liftable[SafeType]

  protected final val liftableDefaultSafeType: Liftable[SafeType] = {
    value =>
      value.use {
        tpe =>
          q"{ $runtimeDIUniverse.SafeType.get[${Liftable.liftType(tpe)}] }"
      }
  }

  /** A hack to support generic methods in macro factories, see `WeakTag`, `GenericAssistedFactory` and associated tests **/
  protected final val liftableUnsafeWeakSafeType: Liftable[SafeType] = {
    value =>
      value.use {
        tpe =>
          q"{ $runtimeDIUniverse.SafeType.unsafeGetWeak[${Liftable.liftType(tpe)}] }"
      }

  }

  // DIKey

  implicit val liftableTypeKey: Liftable[DIKey.TypeKey] = {
    case DIKey.TypeKey(symbol) => q"""
    { new $runtimeDIUniverse.DIKey.TypeKey($symbol) }
      """
  }

  implicit val liftableIdKey: Liftable[DIKey.IdKey[_]] = {
    case idKey: DIKey.IdKey[_] =>
      val lifted = idKey.idContract.liftable(idKey.id)
      q"""{ new $runtimeDIUniverse.DIKey.IdKey(${idKey.tpe}, $lifted) }"""
  }

  implicit val liftableBasicDIKey: Liftable[DIKey.BasicKey] = {
    Liftable[DIKey.BasicKey] {
      case t: DIKey.TypeKey => q"$t"
      case i: DIKey.IdKey[_] => q"${liftableIdKey(i)}"
    }
  }

  // SymbolInfo

  // Symbols may contain uninstantiated poly types, and are usually only included for debugging purposes anyway
  // so weak types are allowed here (See Inject config tests in StaticInjectorTest, they do break if this is changed)
  implicit val liftableSymbolInfo: Liftable[SymbolInfo] = {
    info =>
      q"""{ $runtimeDIUniverse.SymbolInfo.Static(
      name = ${info.name},
      finalResultType = ${liftableUnsafeWeakSafeType(info.finalResultType)},
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
  def apply(u: StaticDIUniverse): DIUniverseLiftables[u.type] = new DIUniverseLiftables[u.type](u) {
    override implicit val liftableSafeType: u.u.Liftable[u.SafeType] = liftableDefaultSafeType
  }

  def generateUnsafeWeakSafeTypes(u: StaticDIUniverse): DIUniverseLiftables[u.type] = new DIUniverseLiftables[u.type](u) {
    override implicit val liftableSafeType: u.u.Liftable[u.SafeType] = liftableUnsafeWeakSafeType
  }
}
