package izumi.distage.model.reflection

final case class AssociationP(symbol: SymbolInfo, key: DIKey) {
  def name: String = symbol.name
  def isByName: Boolean = symbol.isByName
  def withKey(key: DIKey): AssociationP = copy(key = key)

  def wasGeneric: Boolean = symbol.wasGeneric
}
