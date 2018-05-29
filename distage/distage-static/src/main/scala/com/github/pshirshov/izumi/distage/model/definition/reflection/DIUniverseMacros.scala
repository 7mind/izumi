package com.github.pshirshov.izumi.distage.model.definition.reflection

import com.github.pshirshov.izumi.distage.model.reflection.macros.DIUniverseLiftables
import com.github.pshirshov.izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import com.github.pshirshov.izumi.distage.provisioning.AbstractConstructor

class DIUniverseMacros[D <: StaticDIUniverse](override val u: D) extends DIUniverseLiftables[D](u) {
  import u._
  import u.u._

  implicit final val liftableProductWiring: Liftable[Wiring.UnaryWiring.ProductWiring] = {
    case w: Wiring.UnaryWiring.Constructor =>
      q"{ $RuntimeDIUniverse.Wiring.UnaryWiring.Constructor(${w.instanceType}, ${w.associations.toList}) }"

      // FIXME: Macro call in liftable that substitutes for a different type (not just in a different universe...)
    case w: Wiring.UnaryWiring.AbstractSymbol =>
      q"""{
      val fun = ${symbolOf[AbstractConstructor.type].asClass.module}.apply[${w.instanceType.tpe}].function.get

      $RuntimeDIUniverse.Wiring.UnaryWiring.Function.apply(fun, fun.associations)
      }"""
  }
}

object DIUniverseMacros {
  def apply(u: StaticDIUniverse): DIUniverseMacros[u.type] = new DIUniverseMacros[u.type](u)
}
