package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.DIKey

trait LookupInterceptor {
  def interceptLookup[T:Tag](key: DIKey, context: Locator): Option[TypedRef[T]]
}

