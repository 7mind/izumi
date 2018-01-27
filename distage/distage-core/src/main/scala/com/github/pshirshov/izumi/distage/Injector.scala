package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.{Locator, Planner, Producer}

trait Injector extends Planner with Producer {

}

object Injector {
  def emerge(bootstrapContext: Locator): Injector = {
    new InjectorDefaultImpl(bootstrapContext)
  }

  def emerge(): Injector = emerge(DefaultBootstrapContext.instance)
}