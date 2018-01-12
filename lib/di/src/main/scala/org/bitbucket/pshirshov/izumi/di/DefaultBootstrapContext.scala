package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.planning._
import org.bitbucket.pshirshov.izumi.di.reflection.{DependencyKeyProvider, DependencyKeyProviderDefaultImpl, ReflectionProvider, ReflectionProviderDefaultImpl}


trait DefaultBootstrapContext extends Locator {
  override def parent: Option[Locator] = None

  private val planResolverDefaultImpl = new PlanResolverDefaultImpl()
  private val forwardingRefResolverDefaultImpl = new ForwardingRefResolverDefaultImpl()
  private val dependencyKeyProviderDefaultImpl = new DependencyKeyProviderDefaultImpl()
  private val reflectionProviderDefaultImpl = new ReflectionProviderDefaultImpl(dependencyKeyProviderDefaultImpl)
  private val factoryOfFactories = new TheFactoryOfAllTheFactoriesDefaultImpl()
  private val planner = new DefaultPlannerImpl(
    planResolverDefaultImpl
    , forwardingRefResolverDefaultImpl
    , reflectionProviderDefaultImpl
  )

  protected def defaultImpls: Map[DIKey, AnyRef] = Map[DIKey, AnyRef](
    DIKey.get[PlanResolver] -> planResolverDefaultImpl
    , DIKey.get[ForwardingRefResolver] -> forwardingRefResolverDefaultImpl
    , DIKey.get[DependencyKeyProvider] -> dependencyKeyProviderDefaultImpl
    , DIKey.get[TheFactoryOfAllTheFactories] -> factoryOfFactories
    , DIKey.get[ReflectionProvider] -> reflectionProviderDefaultImpl
    , DIKey.get[Planner] -> planner
  )

  def lookup(key: DIKey): Option[AnyRef] = defaultImpls.get(key)
}

object DefaultBootstrapContext {
  final val instance = new DefaultBootstrapContext {}
}
