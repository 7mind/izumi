package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait LookupInterceptor {
  def interceptLookup(key: DIKey, context: Locator): Option[AnyRef]
}

