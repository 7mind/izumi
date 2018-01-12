package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait LookupInterceptor {
  def interceptLookup[T:Tag](key: DIKey, context: Locator): Option[TypedRef[T]]
}

