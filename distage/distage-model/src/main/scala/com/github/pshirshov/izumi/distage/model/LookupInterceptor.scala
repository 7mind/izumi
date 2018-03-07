package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.references.TypedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

trait LookupInterceptor {
  def interceptLookup[T:RuntimeUniverse.Tag](key: RuntimeUniverse.DIKey, context: Locator): Option[TypedRef[T]]
}

