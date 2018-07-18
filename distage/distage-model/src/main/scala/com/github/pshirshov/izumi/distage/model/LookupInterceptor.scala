package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait LookupInterceptor {
  def interceptLookup[T: Tag](key: DIKey, context: Locator): Option[TypedRef[T]]
}
