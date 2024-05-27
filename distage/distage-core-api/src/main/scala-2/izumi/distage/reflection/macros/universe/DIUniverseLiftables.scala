package izumi.distage.reflection.macros.universe

class DIUniverseLiftables[D <: StaticDIUniverse](val u: D) {

  import u.*
  import u.u.*

  protected[this] val modelReflectionPkg: Tree = q"_root_.izumi.distage.model.reflection"

  def liftTypeToSafeType(tpe: TypeNative): Tree = {
    q"{ $modelReflectionPkg.SafeType.get[${Liftable.liftType(tpe)}] }"
  }

  // DIKey

  protected[this] implicit val liftableTypeKey: Liftable[MacroDIKey.TypeKey] = {
    case MacroDIKey.TypeKey(tpe) => q"""
    { new $modelReflectionPkg.DIKey.TypeKey(${liftTypeToSafeType(tpe.typeNative)}) }
      """
  }

  protected[this] implicit val liftableIdKey: Liftable[MacroDIKey.IdKey[?]] = {
    case idKey: MacroDIKey.IdKey[?] =>
      val lifted = idKey.idContract.liftable(idKey.id)
      q"""{ new $modelReflectionPkg.DIKey.IdKey(${liftTypeToSafeType(idKey.tpe.typeNative)}, $lifted) }"""
  }

  protected[this] implicit val liftableBasicDIKey: Liftable[MacroDIKey.BasicKey] = {
    Liftable[MacroDIKey.BasicKey] {
      case t: MacroDIKey.TypeKey => q"${liftableTypeKey(t)}"
      case i: MacroDIKey.IdKey[?] => q"${liftableIdKey(i)}"
    }
  }

  // SymbolInfo

  // currently only function parameter symbols are spliced by this
  // So, `liftableSafeType` is fine and will work, since parameter
  // types must all be resolved anyway - they cannot contain polymorphic
  // components, unlike general method symbols (info for which we don't generate).
  // (annotations always empty currently)
  protected[this] implicit val liftableSymbolInfo: Liftable[MacroSymbolInfo] = {
    info =>
      q"""{ $modelReflectionPkg.SymbolInfo(
      name = ${info.name},
      finalResultType = ${liftTypeToSafeType(info.nonByNameFinalResultType)},
      isByName = ${info.isByName},
      wasGeneric = ${info.wasGeneric}
      ) }"""
  }

  // Associations

  // converts Association.Parameter to LinkedParameter
  implicit val liftableParameter: Liftable[Association.Parameter] = {
    case Association.Parameter(symbol, _, key) =>
      q"new $modelReflectionPkg.LinkedParameter($symbol, $key)"
  }

  implicit val liftableCompactParameter: Liftable[Association.CompactParameter] = {
    case Association.CompactParameter(symbol, _, key) =>
      // TODO: XXX
      q"new $modelReflectionPkg.LinkedParameter(${symbol.asInstanceOf[MacroSymbolInfo]}, $key)"
//      q"new $modelReflectionPkg.CompactParameter(${symbol.asInstanceOf[MacroSymbolInfo]}, $stpe, $key)"
  }
}

object DIUniverseLiftables {
  def apply(u: StaticDIUniverse): DIUniverseLiftables[u.type] = new DIUniverseLiftables[u.type](u)
}
