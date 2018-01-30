package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.plan.{Association, DependencyContext, TypeWiring}
import com.github.pshirshov.izumi.distage.model.references.DIKey

trait DIUniverse extends
       DIUniverseBase
  with DIKey
  with DependencyContext
  with Association
  with TypeWiring
