package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.definition.WithDIAnnotation
import com.github.pshirshov.izumi.distage.model.plan.{WithDIAssociation, WithDIDependencyContext, WithDIWiring}
import com.github.pshirshov.izumi.distage.model.references._
import com.github.pshirshov.izumi.fundamentals.reflection.WithTags

trait DIUniverse
  extends DIUniverseBase
    with WithTags
    with WithDISafeType
    with WithDISymbolInfo
    with WithDITypedRef
    with WithDICallable
    with WithDIKey
    with WithDIDependencyContext
    with WithDIAssociation
    with WithDIWiring
    with WithDIAnnotation
