package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.definition.{ContextDefinition, TrivialDIDef}
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider, SymbolIntrospector}
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.planning._
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}

trait Injector extends Planner with Producer {

}

object Injector {
  def bootstrapDefault(): DefaultBootstrapContext = bootstrap(defaultBootstrapContextDefinition)

  def bootstrapCustomized(overrides: ContextDefinition): DefaultBootstrapContext = {
    bootstrap(defaultBootstrapContextDefinition.overridenBy(overrides))
  }

  def bootstrap(definition: ContextDefinition): DefaultBootstrapContext = {
    new DefaultBootstrapContext(definition)
  }

  def emerge(bootstrapContext: Locator, extensions: LocatorExtension*): Injector = {
    new InjectorDefaultImpl(bootstrapContext.extend(extensions: _*))
  }

  def emerge(extensions: LocatorExtension*): Injector = {
    emerge(defaultBootstrapContext, extensions:_*)
  }

  final lazy val defaultBootstrapContext = bootstrapDefault()

  final lazy val defaultBootstrapContextDefinition: ContextDefinition = TrivialDIDef
    .instance[CustomOpHandler](CustomOpHandler.NullCustomOpHander)
    .instance[LookupInterceptor](NullLookupInterceptor.instance)
    .binding[PlanningHook, PlanningHookDefaultImpl]
    .binding[PlanningObserver, PlanningObserverDefaultImpl]
    .binding[PlanResolver, PlanResolverDefaultImpl]
    .binding[PlanAnalyzer, PlanAnalyzerDefaultImpl]
    .binding[PlanMergingPolicy, PlanMergingPolicyDefaultImpl]
    .binding[TheFactoryOfAllTheFactories, TheFactoryOfAllTheFactoriesDefaultImpl]
    .binding[ForwardingRefResolver, ForwardingRefResolverDefaultImpl]
    .binding[SanityChecker, SanityCheckerDefaultImpl]
    .instance[ReflectionProvider.Java](ReflectionProviderDefaultImpl.Java.instance)
    .instance[SymbolIntrospector.Java](SymbolIntrospectorDefaultImpl.Java.instance)
    .instance[DependencyKeyProvider.Java](DependencyKeyProviderDefaultImpl.Java.instance)
    .binding[Planner, PlannerDefaultImpl]

}
