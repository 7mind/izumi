package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.{Locator, LocatorExtension, Planner, Producer}

trait Injector extends Planner with Producer {

}

object Injector {
  def emerge(bootstrapContext: Locator, extensions: LocatorExtension*): Injector = {
    new InjectorDefaultImpl(bootstrapContext.extend(extensions: _*))
  }

  def emerge(extensions: LocatorExtension*): Injector = {
    emerge(DefaultBootstrapContext.instance, extensions:_*)
  }
}
