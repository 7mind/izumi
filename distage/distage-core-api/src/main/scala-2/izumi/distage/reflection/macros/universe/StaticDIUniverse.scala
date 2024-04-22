package izumi.distage.reflection.macros.universe

import izumi.distage.model.definition.DIStageAnnotation
import izumi.distage.reflection.macros.universe.impl.DIUniverse
import izumi.fundamentals.reflection.SingletonUniverse

import scala.reflect.macros.blackbox

trait StaticDIUniverse extends DIUniverse { self =>
  override val u: SingletonUniverse

  override type MacroIdContract[T] = IdContractImpl[T]
  class IdContractImpl[T: u.Liftable] extends MacroIdContractApi[T] {
    override def repr(v: T): String = v.toString

    val liftable: u.Liftable[T] = implicitly
  }
}

object StaticDIUniverse {
  type Aux[U] = StaticDIUniverse { val u: U }
  def apply(c: blackbox.Context): StaticDIUniverse.Aux[c.universe.type] = {
    new StaticDIUniverse { self =>
      override val u: c.universe.type = c.universe
      override protected val typeOfDistageAnnotation: TypeNative = u.typeOf[DIStageAnnotation]
      override implicit val stringIdContract: MacroIdContract[String] = new IdContractImpl[String]
      override implicit def singletonStringIdContract[S <: String with Singleton]: MacroIdContract[S] = new IdContractImpl[S]
    }
  }
}
