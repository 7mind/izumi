package distage

trait InjectorBootstrap {
  def apply(): Injector

  def apply(overrides: BootstrapModule*): Injector
}
