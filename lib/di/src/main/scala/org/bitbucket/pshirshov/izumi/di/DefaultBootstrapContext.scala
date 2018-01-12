package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.planning._
import org.bitbucket.pshirshov.izumi.di.reflection.{DependencyKeyProvider, DependencyKeyProviderDefaultImpl, ReflectionProvider, ReflectionProviderDefaultImpl}


trait DefaultBootstrapContext extends Locator {
  override def parent: Option[Locator] = None

  // TODO: may we improve it somehow?..
  private val lookupInterceptor = NullLookupInterceptor.instance

  private val factoryOfFactories = new TheFactoryOfAllTheFactoriesDefaultImpl()
  private val planResolver = new PlanResolverDefaultImpl()
  private val dependencyKeyProvider = new DependencyKeyProviderDefaultImpl()

  private val planAnalyzer = new PlanAnalyzerDefaultImpl()
  private val forwardingRefResolver = new ForwardingRefResolverDefaultImpl(planAnalyzer)
  private val sanityChecker = new SanityCheckerDefaultImpl(planAnalyzer)

  private val reflectionProviderDefaultImpl = new ReflectionProviderDefaultImpl(dependencyKeyProvider)
  private val customOpHandler = CustomOpHandler.NullCustomOpHander

  private val planner = new DefaultPlannerImpl(
    planResolver
    , forwardingRefResolver
    , reflectionProviderDefaultImpl
    , sanityChecker
    , customOpHandler
  )

  protected def defaultImpls: Map[DIKey, AnyRef] = Map[DIKey, AnyRef](
    DIKey.get[PlanResolver] -> planResolver
    , DIKey.get[ForwardingRefResolver] -> forwardingRefResolver
    , DIKey.get[DependencyKeyProvider] -> dependencyKeyProvider
    , DIKey.get[TheFactoryOfAllTheFactories] -> factoryOfFactories
    , DIKey.get[ReflectionProvider] -> reflectionProviderDefaultImpl
    , DIKey.get[Planner] -> planner
    , DIKey.get[LookupInterceptor] -> lookupInterceptor
    , DIKey.get[SanityChecker] -> sanityChecker
    , DIKey.get[CustomOpHandler] -> customOpHandler
  )

  protected def unsafeLookup(key:DIKey): Option[AnyRef] = defaultImpls.get(key)
}

object DefaultBootstrapContext {
  final val instance = new DefaultBootstrapContext {}
}
