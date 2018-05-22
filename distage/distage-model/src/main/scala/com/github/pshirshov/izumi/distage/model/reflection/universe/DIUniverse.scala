package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.definition.WithDIAnnotation
import com.github.pshirshov.izumi.distage.model.plan.{WithDIAssociation, WithDIDependencyContext, WithDIWiring}
import com.github.pshirshov.izumi.distage.model.references._

trait DIUniverse
  extends DIUniverseBase
    with WithDISafeType
    with WithDISymbolInfo
    with WithDITypedRef
    with WithDICallable
    with WithDIKey
    with WithDIDependencyContext
    with WithDIAssociation
    with WithDIWiring
    with WithDIAnnotation
