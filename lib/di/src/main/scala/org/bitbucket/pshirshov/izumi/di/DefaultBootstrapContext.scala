package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.FinalPlan
import org.bitbucket.pshirshov.izumi.di.planning._
import org.bitbucket.pshirshov.izumi.di.provisioning.ProvisionerDefaultImpl
import org.bitbucket.pshirshov.izumi.di.reflection.{DependencyKeyProvider, DependencyKeyProviderDefaultImpl, ReflectionProvider, ReflectionProviderDefaultImpl}


trait DefaultBootstrapContext extends Locator {
  override def parent: Option[Locator] = None

  // TODO: may we improve it somehow?..
  private val lookupInterceptor = NullLookupInterceptor.instance

  private val provisioner = new ProvisionerDefaultImpl
  private val factoryOfFactories = new TheFactoryOfAllTheFactoriesDefaultImpl(provisioner)
  private val planningObsever = new PlanningObserverDefaultImpl()

  private val planResolver = new PlanResolverDefaultImpl()
  private val dependencyKeyProvider = new DependencyKeyProviderDefaultImpl()

  private val planAnalyzer = new PlanAnalyzerDefaultImpl()
  private val forwardingRefResolver = new ForwardingRefResolverDefaultImpl(planAnalyzer)
  private val sanityChecker = new SanityCheckerDefaultImpl(planAnalyzer)

  private val reflectionProviderDefaultImpl = new ReflectionProviderDefaultImpl(dependencyKeyProvider)
  private val customOpHandler = CustomOpHandler.NullCustomOpHander

  private val planMergingPolicy = new PlanMergingPolicyDefaultImpl()

  private val planner = new PlannerDefaultImpl(
    planResolver
    , forwardingRefResolver
    , reflectionProviderDefaultImpl
    , sanityChecker
    , customOpHandler
    , planningObsever
    , planMergingPolicy
  )

  // TODO: may we bootstrap ourself somehow?
  override def plan: FinalPlan =
    throw new UnsupportedOperationException(s"Bootstrap context is provisioned manually, not planned.")

  protected def defaultImpls: Map[DIKey, Any] = Map[DIKey, Any](
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

  override protected def unsafeLookup(key: DIKey): Option[Any] =
    defaultImpls.get(key)

  override def enumerate: Stream[IdentifiedRef] = defaultImpls.map {
    IdentifiedRef.tupled
  }.toStream
}

object DefaultBootstrapContext {
  final val instance: DefaultBootstrapContext = new DefaultBootstrapContext {}
}
