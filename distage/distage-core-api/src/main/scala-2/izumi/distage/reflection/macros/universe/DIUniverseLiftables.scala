package izumi.distage.reflection.macros.universe

import izumi.distage.reflection.macros.universe.impl.{CompactParameter, MacroDIKey, MacroSafeType}

class DIUniverseLiftables[D <: StaticDIUniverse](val u: D) {

  import u.*
  import u.u.*

  protected[this] val modelReflectionPkg: Tree = q"_root_.izumi.distage.model.reflection"

  def liftMacroTypeToSafeType(tpe: MacroSafeType): Tree = {
    tpe.tagTree.asInstanceOf[Tree]
  }

  private def liftTypeToSafeType(tpe: TypeNative): Tree = {
    q"{ $modelReflectionPkg.SafeType.get[${Liftable.liftType(tpe)}] }"
  }

  // DIKey
  protected[this] implicit val liftableTypeKey: Liftable[MacroDIKey.TypeKey] = {
    case MacroDIKey.TypeKey(tpe) => q"""
    { new $modelReflectionPkg.DIKey.TypeKey(${liftMacroTypeToSafeType(tpe)}) }
      """
  }

  protected[this] implicit val liftableIdKey: Liftable[MacroDIKey.IdKey] = {
    case idKey: MacroDIKey.IdKey =>
      q"""{ new $modelReflectionPkg.DIKey.IdKey(${liftMacroTypeToSafeType(idKey.tpe)}, ${idKey.id}) }"""
  }

  protected[this] implicit val liftableBasicDIKey: Liftable[MacroDIKey.BasicKey] = {
    Liftable[MacroDIKey.BasicKey] {
      case t: MacroDIKey.TypeKey => q"${liftableTypeKey(t)}"
      case i: MacroDIKey.IdKey => q"${liftableIdKey(i)}"
    }
  }

  // SymbolInfo

//  protected[this] def lifSymbolInfo(info: MacroSymbolInfo): Tree = {}

  // Associations

  // converts Association.Parameter to LinkedParameter
  implicit val liftableParameter: Liftable[Association.Parameter] = {
    case Association.Parameter(info, _, key) =>
      // currently only function parameter symbols are spliced by this
      // So, `liftableSafeType` is fine and will work, since parameter
      // types must all be resolved anyway - they cannot contain polymorphic
      // components, unlike general method symbols (info for which we don't generate).
      // (annotations always empty currently)
      val symTree = q"""{ $modelReflectionPkg.SymbolInfo(
      name = ${info.name},
      finalResultType = ${liftTypeToSafeType(info.nonByNameFinalResultType)},
      isByName = ${info.isByName},
      wasGeneric = ${info.wasGeneric}
      ) }"""

      q"new $modelReflectionPkg.LinkedParameter($symTree, $key)"
  }

  implicit val liftableCompactParameter: Liftable[CompactParameter] = {
    case CompactParameter(info, _, key) =>
//      val symTree = lifSymbolInfo(symbol.asInstanceOf[MacroSymbolInfo])
      val symTree = q"""{ $modelReflectionPkg.SymbolInfo(
      name = ${info.name},
      finalResultType = ${liftTypeToSafeType(info.asInstanceOf[MacroSymbolInfo].nonByNameFinalResultType)},
      isByName = ${info.isByName},
      wasGeneric = ${info.wasGeneric}
      ) }"""
      q"new $modelReflectionPkg.LinkedParameter($symTree, $key)"
  }
}

object DIUniverseLiftables {
  def apply(u: StaticDIUniverse): DIUniverseLiftables[u.type] = new DIUniverseLiftables[u.type](u)
}
