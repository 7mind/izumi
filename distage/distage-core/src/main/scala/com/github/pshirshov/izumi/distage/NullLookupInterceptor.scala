package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.references.TypedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import com.github.pshirshov.izumi.distage.model.{Locator, LookupInterceptor}

class NullLookupInterceptor extends LookupInterceptor {
  def interceptLookup[T:RuntimeUniverse.Tag](key: RuntimeUniverse.DIKey, context: Locator): Option[TypedRef[T]] = None
}

object NullLookupInterceptor {
  final val instance = new NullLookupInterceptor()
}
