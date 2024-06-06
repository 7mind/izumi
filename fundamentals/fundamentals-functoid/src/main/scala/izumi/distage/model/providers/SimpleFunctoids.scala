package izumi.distage.model.providers

import izumi.distage.model.reflection.*
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.reflect.Tag

private[providers] trait SimpleFunctoids[Ftoid[+_]] {
  protected def create[A](provider: Provider): Ftoid[A]

  def pure[A: Tag](a: A): Ftoid[A] = lift(a)

  def unit: Ftoid[Unit] = pure(())

  def identity[A: Tag]: Ftoid[A] = identityKey(DIKey.get[A]).asInstanceOf[Ftoid[A]]

  def lift[A: Tag](a: => A): Ftoid[A] = {
    create[A](
      Provider.ProviderImpl[A](
        parameters = Seq.empty,
        ret = SafeType.get[A],
        underlying = () => a,
        fun = (_: Seq[Any]) => a,
        providerType = ProviderType.Function,
      )
    )
  }

  def singleton[A <: Singleton: Tag](a: A): Ftoid[A] = {
    create[A](
      Provider.ProviderImpl[A](
        parameters = Seq.empty,
        ret = SafeType.get[A],
        underlying = a.asInstanceOf[AnyRef],
        fun = (_: Seq[Any]) => a,
        providerType = ProviderType.Constructor,
      )
    )
  }

  def single[A: Tag, B: Tag](f: A => B): Ftoid[B] = {
    val key = DIKey.get[A]
    val tpe = key.tpe
    val retTpe = SafeType.get[B]
    val symbolInfo = firstParamSymbolInfo(tpe)

    create[B](
      Provider.ProviderImpl(
        parameters = Seq(LinkedParameter(symbolInfo, key)),
        ret = retTpe,
        underlying = f,
        fun = (s: Seq[Any]) => f(s.head.asInstanceOf[A]),
        providerType = ProviderType.Function,
      )
    )
  }

  def identityKey[A](key: DIKey): Ftoid[A] = {
    val tpe = key.tpe
    val symbolInfo = firstParamSymbolInfo(tpe)

    create[A](
      Provider.ProviderImpl(
        parameters = Seq(LinkedParameter(symbolInfo, key)),
        ret = tpe,
        fun = (_: Seq[Any]).head,
        providerType = ProviderType.Function,
      )
    )
  }

  @inline private def firstParamSymbolInfo(tpe: SafeType): SymbolInfo = {
    SymbolInfo(
      name = "x$1",
      finalResultType = tpe,
      isByName = false,
      wasGeneric = false,
    )
  }
}
