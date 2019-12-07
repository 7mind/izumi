package izumi.distage.model.reflection.universe

import izumi.distage.model.plan.operations.{WithDIAssociation, WithDIWiring}
import izumi.distage.model.references._

trait DIUniverse
  extends DIUniverseBase
    with WithDISafeType
    with WithDISymbolInfo
    with WithDITypedRef
    with WithDICallable
    with WithDIKey
    with WithDIAssociation
    with WithDIWiring



