package izumi.distage.reflection.macros.universe

class DIUniverseLiftables[D <: StaticDIUniverse](val u: D) {

  import u.*
  import u.u.*

  protected[this] val modelReflectionPkg: Tree = q"_root_.izumi.distage.model.reflection"

  def liftTypeToSafeType(tpe: TypeNative): Tree = {
    q"{ $modelReflectionPkg.SafeType.get[${Liftable.liftType(tpe)}] }"
  }

  def liftMacroTypeToSafeType(tpe: MacroSafeType): Tree = {
    liftTypeToSafeType(tpe.typeNative)
  }

  // DIKey
  protected[this] implicit val liftableTypeKey: Liftable[MacroDIKey.TypeKey] = {
    case MacroDIKey.TypeKey(tpe) => q"""
    { new $modelReflectionPkg.DIKey.TypeKey(${liftMacroTypeToSafeType(tpe)}) }
      """
  }

  protected[this] implicit val liftableIdKey: Liftable[MacroDIKey.IdKey[?]] = {
    case idKey: MacroDIKey.IdKey[?] =>
      val lifted = idKey.idContract.liftable(idKey.id)
      q"""{ new $modelReflectionPkg.DIKey.IdKey(${liftMacroTypeToSafeType(idKey.tpe)}, $lifted) }"""
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
  protected[this] def lifSymbolInfo(info: MacroSymbolInfo): Tree = {
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
      val symTree = lifSymbolInfo(symbol)
      q"new $modelReflectionPkg.LinkedParameter($symTree, $key)"
  }

  implicit val liftableCompactParameter: Liftable[Association.CompactParameter] = {
    case Association.CompactParameter(symbol, _, key) =>
      val symTree = lifSymbolInfo(symbol.asInstanceOf[MacroSymbolInfo])
      q"new $modelReflectionPkg.LinkedParameter($symTree, $key)"
  }
}

object DIUniverseLiftables {
  def apply(u: StaticDIUniverse): DIUniverseLiftables[u.type] = new DIUniverseLiftables[u.type](u)
}
