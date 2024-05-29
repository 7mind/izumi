package izumi.distage.reflection.macros.universe

import izumi.distage.model.definition.DIStageAnnotation
import izumi.distage.reflection.macros.universe.impl.DIUniverse
import izumi.fundamentals.reflection.SingletonUniverse

import scala.reflect.macros.blackbox

trait StaticDIUniverse extends DIUniverse { self =>
  override val u: SingletonUniverse
}

object StaticDIUniverse {
  type Aux[U] = StaticDIUniverse { val u: U }
  def apply(c: blackbox.Context): StaticDIUniverse.Aux[c.universe.type] = {
    new StaticDIUniverse { self =>
      override val ctx: blackbox.Context = c
      override val u: c.universe.type = c.universe
      override protected val typeOfDistageAnnotation: TypeNative = u.typeOf[DIStageAnnotation]

      override val rp: ReflectionProvider = ReflectionProviderDefaultImpl.apply(this)
    }
  }
}
