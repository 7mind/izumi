package izumi.distage.model.providers

import izumi.distage.model.exceptions.runtime.TODOBindingException
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection.*
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

private[providers] trait SimpleFunctoidsTemplate { this: FunctoidTemplate =>

  private[providers] trait SimpleFunctoids {
    def identity[A: Tag]: Functoid[A] = identityKey(DIKey.get[A]).asInstanceOf[Functoid[A]]

    def lift[A: Tag](a: => A): Functoid[A] = {
      new Functoid[A](
        Provider.ProviderImpl[A](
          parameters = Seq.empty,
          ret = SafeType.get[A],
          underlying = () => a,
          fun = (_: Seq[Any]) => a,
          providerType = ProviderType.Function,
        )
      )
    }

    def singleton[A <: Singleton: Tag](a: A): Functoid[A] = {
      new Functoid[A](
        Provider.ProviderImpl[A](
          parameters = Seq.empty,
          ret = SafeType.get[A],
          underlying = a.asInstanceOf[AnyRef],
          fun = (_: Seq[Any]) => a,
          providerType = ProviderType.Constructor,
        )
      )
    }

    def single[A: Tag, B: Tag](f: A => B): Functoid[B] = {
      val key = DIKey.get[A]
      val tpe = key.tpe
      val retTpe = SafeType.get[B]
      val symbolInfo = firstParamSymbolInfo(tpe)

      new Functoid[B](
        Provider.ProviderImpl(
          parameters = Seq(LinkedParameter(symbolInfo, key)),
          ret = retTpe,
          underlying = f,
          fun = (s: Seq[Any]) => f(s.head.asInstanceOf[A]),
          providerType = ProviderType.Function,
        )
      )
    }

    def todoProvider(key: DIKey)(implicit pos: CodePositionMaterializer): Functoid[Nothing] = {
      new Functoid[Nothing](
        Provider.ProviderImpl(
          parameters = Seq.empty,
          ret = key.tpe,
          fun = _ => throw new TODOBindingException(s"Tried to instantiate a 'TODO' binding for $key defined at ${pos.get}!", key, pos),
          providerType = ProviderType.Function,
        )
      )
    }

    def identityKey(key: DIKey): Functoid[?] = {
      val tpe = key.tpe
      val symbolInfo = firstParamSymbolInfo(tpe)

      new Functoid(
        Provider.ProviderImpl(
          parameters = Seq(LinkedParameter(symbolInfo, key)),
          ret = tpe,
          fun = (_: Seq[Any]).head,
          providerType = ProviderType.Function,
        )
      )
    }

    @inline private[this] def firstParamSymbolInfo(tpe: SafeType): SymbolInfo = {
      SymbolInfo(
        name = "x$1",
        finalResultType = tpe,
        isByName = false,
        wasGeneric = false,
      )
    }
  }

}
