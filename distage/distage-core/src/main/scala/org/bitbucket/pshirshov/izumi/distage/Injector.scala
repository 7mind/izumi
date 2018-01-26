package org.bitbucket.pshirshov.izumi.distage

trait Injector extends Planner with Producer {

}

object Injector {
  def emerge(bootstrapContext: Locator): Injector = {
    new InjectorDefaultImpl(bootstrapContext)
  }

  def emerge(): Injector = emerge(DefaultBootstrapContext.instance)
}