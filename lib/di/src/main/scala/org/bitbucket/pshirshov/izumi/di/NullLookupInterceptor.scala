package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey

class NullLookupInterceptor extends LookupInterceptor {
  override def interceptLookup(key: DIKey, context: Locator): Option[AnyRef] = None
}

object NullLookupInterceptor {
  final val instance = new NullLookupInterceptor()
}
