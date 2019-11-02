package izumi.distage.model.reflection.universe

import izumi.distage.model.definition.WithDIAnnotation
import izumi.distage.model.plan.{WithDIAssociation, WithDIDependencyContext, WithDIWiring}
import izumi.distage.model.references._

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



