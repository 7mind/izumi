package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.model.DIKey

class NullLookupInterceptor extends LookupInterceptor {
  def interceptLookup[T:Tag](key: DIKey, context: Locator): Option[TypedRef[T]] = None
}

object NullLookupInterceptor {
  final val instance = new NullLookupInterceptor()
}
