package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.definition.WithDIAnnotation
import com.github.pshirshov.izumi.distage.model.plan.{WithDIAssociation, WithDIDependencyContext, WithDIWiring}
import com.github.pshirshov.izumi.distage.model.references._

trait DIUniverse
  extends DIUniverseBase
    with DILiftableRuntimeUniverse
    with WithDISafeType
    with WithDITypedRef
    with WithDICallable
    with WithDIKey
    with WithDIDependencyContext
    with WithDIAssociation
    with WithDIWiring
    with WithDIAnnotation

trait DILiftableRuntimeUniverse {
  this: DIUniverseBase =>

  import u._

  implicit final val liftableRuntimeUniverse: Liftable[RuntimeDIUniverse.type] = { _: RuntimeDIUniverse.type => q"${symbolOf[RuntimeDIUniverse.type].asClass.module}" }

}
