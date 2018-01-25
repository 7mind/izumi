package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.model.DIKey

trait LookupInterceptor {
  def interceptLookup[T:Tag](key: DIKey, context: Locator): Option[TypedRef[T]]
}

