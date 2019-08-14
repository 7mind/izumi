package izumi.distage.model.reflection.universe

import izumi.distage.model.definition.DIStageAnnotation
import izumi.fundamentals.reflection.SingletonUniverse

import scala.reflect.macros.blackbox

trait StaticDIUniverse extends DIUniverse {
  override val u: SingletonUniverse

  class IdContractImpl[T: u.Liftable] extends IdContract[T] {
    override def repr(v: T): String = v.toString

    val liftable: u.Liftable[T] = implicitly
  }

}

object StaticDIUniverse {
  type Aux[U] = StaticDIUniverse { val u: U }

  def apply(c: blackbox.Context): StaticDIUniverse.Aux[c.universe.type] = new StaticDIUniverse {
    override val u: c.universe.type = c.universe

    override protected val typeOfDistageAnnotation: SafeType = SafeType.get[DIStageAnnotation]
    override implicit val stringIdContract: IdContract[String] = new IdContractImpl[String]
    override implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S] = new IdContractImpl[S]
  }
}
