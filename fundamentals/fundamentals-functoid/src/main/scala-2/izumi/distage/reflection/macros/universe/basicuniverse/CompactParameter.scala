package izumi.distage.reflection.macros.universe.basicuniverse

final case class CompactParameter(symbol: MacroSymbolInfoCompact, key: MacroDIKey.BasicKey) {
  def isByName: Boolean = symbol.isByName
}
