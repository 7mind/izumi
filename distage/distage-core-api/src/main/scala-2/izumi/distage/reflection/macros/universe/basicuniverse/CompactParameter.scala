package izumi.distage.reflection.macros.universe.basicuniverse

case class CompactParameter(symbol: MacroSymbolInfoCompact, stpe: MacroSafeType, key: MacroDIKey.BasicKey) {
  final def isByName: Boolean = symbol.isByName
}
