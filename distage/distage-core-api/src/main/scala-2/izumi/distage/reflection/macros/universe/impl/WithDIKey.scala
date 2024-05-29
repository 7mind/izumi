package izumi.distage.reflection.macros.universe.impl

private[distage] trait WithDIKey { this: DIUniverseBase =>

  sealed trait MacroDIKey {
    def tpe: MacroSafeType
  }

  object MacroDIKey {
    sealed trait BasicKey extends MacroDIKey {
      def withTpe(tpe: MacroSafeType): MacroDIKey.BasicKey
    }

    case class TypeKey(tpe: MacroSafeType) extends BasicKey {
      final def named[I: MacroIdContract](id: I): IdKey[I] = IdKey(tpe, id)

      override final def withTpe(tpe: MacroSafeType): MacroDIKey.TypeKey = copy(tpe = tpe)
      override final def toString: String = s"{type.${tpe.toString}}"
    }

    case class IdKey[I: MacroIdContract](tpe: MacroSafeType, id: I) extends BasicKey {
      final val idContract: MacroIdContract[I] = implicitly

      override final def withTpe(tpe: MacroSafeType): MacroDIKey.IdKey[I] = copy(tpe = tpe)
      override final def toString: String = s"{type.${tpe.toString}@${idContract.repr(id)}}"
    }
  }

  trait MacroIdContractApi[T] {
    def repr(v: T): String
  }
  type MacroIdContract[T] <: MacroIdContractApi[T]

  implicit def stringIdContract: MacroIdContract[String]
  implicit def singletonStringIdContract[S <: String with Singleton]: MacroIdContract[S]
}
