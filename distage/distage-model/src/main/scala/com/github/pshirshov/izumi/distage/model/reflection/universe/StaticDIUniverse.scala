package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.reflection.macros.WithDILiftables
import com.github.pshirshov.izumi.fundamentals.reflection.SingletonUniverse

import scala.reflect.macros.blackbox

trait StaticDIUniverse0 {
  this: DIUniverse =>

  override val u: SingletonUniverse

  class IdContractImpl[T: u.Liftable] extends IdContract[T] {
    override def repr(v: T): String = v.toString

    val liftable: u.Liftable[T] = implicitly[u.Liftable[T]]
  }
}

trait StaticDIUniverse extends DIUniverse with StaticDIUniverse0 with WithDILiftables {
}

object StaticDIUniverse {
  type Aux[U] = StaticDIUniverse { val u: U }

  def apply(c: blackbox.Context): StaticDIUniverse.Aux[c.universe.type] = new StaticDIUniverse {
    override val u: c.universe.type = c.universe

    override implicit val stringIdContract: IdContract[String] = new IdContractImpl[String]
    override implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S] = new IdContractImpl[S]
  }
}
