package izumi.distage.model.reflection

final case class LinkedParameter(symbol: SymbolInfo, key: DIKey) {
  def name: String = symbol.name
  def isByName: Boolean = symbol.isByName
  def wasGeneric: Boolean = symbol.wasGeneric

  @inline def replaceKey(f: DIKey => DIKey): LinkedParameter = copy(key = f(key))
}
