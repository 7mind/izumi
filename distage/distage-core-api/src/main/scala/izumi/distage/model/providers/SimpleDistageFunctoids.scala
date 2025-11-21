package izumi.distage.model.providers

import izumi.distage.model.exceptions.runtime.TODOBindingException
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection.{DIKey, Provider}
import izumi.fundamentals.platform.language.CodePositionMaterializer

trait SimpleDistageFunctoids {
  def todoProvider(key: DIKey)(implicit pos: CodePositionMaterializer): Functoid[Nothing] = {
    Functoid.create[Nothing](
      Provider.ProviderImpl(
        parameters = Seq.empty,
        ret = key.tpe,
        fun = _ => throw new TODOBindingException(s"Tried to instantiate a 'TODO' binding for $key defined at ${pos.get}!", key, pos),
        providerType = ProviderType.Function,
      )
    )
  }
}
