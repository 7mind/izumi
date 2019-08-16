package izumi.distage.model.reflection.universe

import izumi.distage.model.definition.DIStageAnnotation
import izumi.fundamentals.reflection.{SingletonUniverse, Tags}

import scala.reflect.macros.blackbox

trait StaticDIUniverse extends DIUniverse { self =>
  override val u: SingletonUniverse

  class IdContractImpl[T: u.Liftable] extends IdContract[T] {
    override def repr(v: T): String = v.toString

    val liftable: u.Liftable[T] = implicitly
  }
}

object StaticDIUniverse {
  type Aux[U] = StaticDIUniverse { val u: U }

  def apply(c: blackbox.Context): StaticDIUniverse.Aux[c.universe.type] = {
    val tags0 = new Tags { val u: c.universe.type = c.universe }
    new StaticDIUniverse { self =>
      override val u: c.universe.type = c.universe
      override lazy val tags: tags0.type = tags0
      override protected val typeOfDistageAnnotation: SafeType = SafeType(u.typeOf[DIStageAnnotation])
      override implicit val stringIdContract: IdContract[String] = new IdContractImpl[String]
      override implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S] = new IdContractImpl[S]
    }
  }
}
