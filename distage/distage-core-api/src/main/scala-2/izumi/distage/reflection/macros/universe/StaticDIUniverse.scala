package izumi.distage.reflection.macros.universe

import izumi.distage.model.definition.DIStageAnnotation
import izumi.distage.reflection.macros.universe.impl.DIUniverse
import izumi.fundamentals.reflection.SingletonUniverse

trait StaticDIUniverse extends DIUniverse { self =>
  override val u: SingletonUniverse
}

object StaticDIUniverse {
  type Aux[U] = StaticDIUniverse { val u: U }
  def apply(universe: scala.reflect.api.Universe): StaticDIUniverse.Aux[universe.type] = {
    new StaticDIUniverse { self =>
      override val u: universe.type = universe
      override protected val typeOfDistageAnnotation: TypeNative = u.typeOf[DIStageAnnotation]
    }
  }
}
