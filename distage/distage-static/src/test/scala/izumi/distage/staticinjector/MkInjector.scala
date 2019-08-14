package izumi.distage.staticinjector


import distage.{Injector, BootstrapModule}

trait MkInjector {

  def mkInjector(overrides: BootstrapModule*): Injector = Injector.NoCogen(overrides: _*)

  def mkInjectorWithProxy(): Injector = Injector.Standard()

}
