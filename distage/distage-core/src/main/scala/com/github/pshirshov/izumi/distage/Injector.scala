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
  import DefaultBootstrapContext._

  private def bootstrapDefault(): DefaultBootstrapContext = bootstrap(defaultBootstrapContextDefinition)

  private final lazy val defaultBootstrapContext = bootstrapDefault()

  private def bootstrap(definition: ContextDefinition): DefaultBootstrapContext = {
    new DefaultBootstrapContext(definition)
  }

  def emerge(overrides: ContextDefinition, extensions: LocatorExtension*): Injector = {
    emerge(bootstrap(defaultBootstrapContextDefinition.overridenBy(overrides)), extensions :_*)
  }

  def emerge(bootstrapContext: Locator, extensions: LocatorExtension*): Injector = {
    new InjectorDefaultImpl(bootstrapContext.extend(extensions: _*))
  }

  def emerge(extensions: LocatorExtension*): Injector = {
    emerge(defaultBootstrapContext, extensions:_*)
  }




}
