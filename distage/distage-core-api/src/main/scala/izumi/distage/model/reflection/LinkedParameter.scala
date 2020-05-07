package izumi.distage.model.reflection

final case class LinkedParameter(symbol: SymbolInfo, key: DIKey) {
  def name: String = symbol.name
  def isByName: Boolean = symbol.isByName
  def withKey(key: DIKey): LinkedParameter = copy(key = key)

  def wasGeneric: Boolean = symbol.wasGeneric
}
