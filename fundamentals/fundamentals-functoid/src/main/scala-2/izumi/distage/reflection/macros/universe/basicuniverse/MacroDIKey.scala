package izumi.distage.reflection.macros.universe.basicuniverse

sealed trait MacroDIKey {
  def tpe: MacroSafeType
}

object MacroDIKey {
  sealed trait BasicKey extends MacroDIKey {
    def withTpe(tpe: MacroSafeType): MacroDIKey.BasicKey
  }

  case class TypeKey(tpe: MacroSafeType) extends BasicKey {
    final def named(id: String): IdKey = IdKey(tpe, id)

    override final def withTpe(tpe: MacroSafeType): MacroDIKey.TypeKey = copy(tpe = tpe)
    override final def toString: String = s"{type.${tpe.toString}}"
  }

  case class IdKey(tpe: MacroSafeType, id: String) extends BasicKey {
    override final def withTpe(tpe: MacroSafeType): MacroDIKey.IdKey = copy(tpe = tpe)
    override final def toString: String = s"{type.${tpe.toString}@$id}"
  }
}
