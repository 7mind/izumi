package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey

class NullLookupInterceptor extends LookupInterceptor {
  def interceptLookup[T:Tag](key: DIKey, context: Locator): Option[TypedRef[T]] = None
}

object NullLookupInterceptor {
  final val instance = new NullLookupInterceptor()
}
