package izumi.distage.model.reflection

sealed trait Association {
  def symbol: SymbolInfo
  def key: DIKey
  def name: String
  def isByName: Boolean

  def withKey(key: DIKey): Association
}

object Association {
  final case class Parameter(symbol: SymbolInfo, key: DIKey) extends Association {
    override final def name: String = symbol.name
    override final def isByName: Boolean = symbol.isByName
    override final def withKey(key: DIKey): Association.Parameter = copy(key = key)

    final def wasGeneric: Boolean = symbol.wasGeneric
  }
}
