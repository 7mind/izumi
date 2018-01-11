package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.planning.{ForwardingRefResolver, ForwardingRefResolverDefaultImpl, PlanResolver, PlanResolverDefaultImpl}
import org.bitbucket.pshirshov.izumi.di.reflection.{DependencyKeyProvider, DependencyKeyProviderDefaultImpl, ReflectionProvider, ReflectionProviderDefaultImpl}



object DefaultBootstrapContext extends Locator {
  override def parent: Option[Locator] = None
  private val planResolverDefaultImpl = new PlanResolverDefaultImpl()
  private val forwardingRefResolverDefaultImpl = new ForwardingRefResolverDefaultImpl()
  private val dependencyKeyProviderDefaultImpl = new DependencyKeyProviderDefaultImpl()
  private val reflectionProviderDefaultImpl = new ReflectionProviderDefaultImpl(dependencyKeyProviderDefaultImpl)

  private val defaultImpls = Map[DIKey, AnyRef](
    DIKey.get[PlanResolver] -> planResolverDefaultImpl
    , DIKey.get[ForwardingRefResolver] -> forwardingRefResolverDefaultImpl
    , DIKey.get[DependencyKeyProvider] -> dependencyKeyProviderDefaultImpl
    , DIKey.get[ReflectionProvider] -> reflectionProviderDefaultImpl
  )

  def lookup(key: DIKey): Option[AnyRef] = defaultImpls.get(key)
}
