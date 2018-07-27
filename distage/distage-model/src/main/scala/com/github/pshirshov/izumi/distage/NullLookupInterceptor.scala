package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.{Locator, LookupInterceptor}

class NullLookupInterceptor extends LookupInterceptor {
  def interceptLookup[T: Tag](key: DIKey, context: Locator): Option[TypedRef[T]] = None
}

object NullLookupInterceptor extends NullLookupInterceptor
