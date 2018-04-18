package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.plan.{Association, DependencyContext, Wiring}
import com.github.pshirshov.izumi.distage.model.references.DIKey

trait DIUniverse
  extends DIUniverseBase
     with SafeType
     with Callable
     with DIKey
     with DependencyContext
     with Association
     with Wiring
