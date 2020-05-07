package izumi.distage.model.reflection

sealed trait AssociationP {
  def symbol: SymbolInfo
  def key: DIKey
  def name: String
  def isByName: Boolean

  def withKey(key: DIKey): AssociationP
}

object AssociationP {
  final case class Parameter(symbol: SymbolInfo, key: DIKey) extends AssociationP {
    override final def name: String = symbol.name
    override final def isByName: Boolean = symbol.isByName
    override final def withKey(key: DIKey): AssociationP.Parameter = copy(key = key)

    final def wasGeneric: Boolean = symbol.wasGeneric
  }
}
