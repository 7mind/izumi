package com.github.pshirshov.izumi.distage.injector

import distage.Injector

trait MkInjector {
  def mkInjector(): Injector = Injector.Standard()
}
