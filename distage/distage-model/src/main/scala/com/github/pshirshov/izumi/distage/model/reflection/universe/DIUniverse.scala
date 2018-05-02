package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.definition.DIAnnotation
import com.github.pshirshov.izumi.distage.model.plan.{DIAssociation, DIDependencyContext, DIWiring}
import com.github.pshirshov.izumi.distage.model.references.{DIKey, DITypedRef}

trait DIUniverse
  extends DIUniverseBase
     with DISafeType
     with DITypedRef
     with DICallable
     with DIKey
     with DIDependencyContext
     with DIAssociation
     with DIWiring
     with DIAnnotation
     with DILiftableRuntimeUniverse

trait DILiftableRuntimeUniverse {
  this: DIUniverseBase =>

  import u._
  implicit final val liftableRuntimeUniverse: Liftable[RuntimeDIUniverse.type] =
    { _: RuntimeDIUniverse.type => q"${symbolOf[RuntimeDIUniverse.type].asClass.module}" }

}
