package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.DIContext
import org.bitbucket.pshirshov.izumi.di.model.DIKey



object DefaultBootstrapContext extends DIContext {
  private val defaultImpls = Map[DIKey, AnyRef](
    DIKey.get[PlanResolver] -> new PlanResolverDefaultImpl()
    , DIKey.get[ForwardingRefResolver] -> new ForwardingRefResolverDefaultImpl()
  )

  def lookup(key: DIKey): Option[AnyRef] = defaultImpls.get(key)
}
