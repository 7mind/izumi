package izumi.distage

import izumi.distage.model.definition.{Activation, BootstrapContextModule, BootstrapModule, Module}
import izumi.distage.model.effect.QuasiEffect
import izumi.distage.model.recursive.Bootloader
import izumi.distage.model.{Injector, Locator, PlannerInput}
import izumi.distage.modules.DefaultModule
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

trait InjectorFactory {

  /**
    * Create a new Injector
    *
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def apply[F[_]: QuasiEffect: TagK: DefaultModule](overrides: BootstrapModule*): Injector[F]

  /**
    * Create a new Injector from a custom [[izumi.distage.model.definition.BootstrapContextModule]]
    *
    * @param bootstrapBase See [[izumi.distage.bootstrap.BootstrapLocator.defaultBootstrap]]
    * @param overrides     Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                      They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def apply[F[_]: QuasiEffect: TagK: DefaultModule](bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector[F]

  /**
    * Create a new default Injector with [[izumi.fundamentals.platform.functional.Identity]] effect type
    *
    * Use [[apply[F[_]*]] variants to specify a different effect type
    */
  // Note: this function exists only because of Scala 2.12's sub-par implicit handling,
  // 2.12 fails to default to `QuasiEffect.QuasiEffectIdentity` when writing `Injector()`
  // if cats-effect is on classpath, because of recursive (on 2.12: diverging) instances in `cats.effect.Sync`
  def apply(): Injector[Identity]

  /**
    * Create a new Injector with chosen [[izumi.distage.model.definition.Activation]] axes for the bootstrap environment.
    * The passed activation will affect _only_ the bootstrapping of the injector itself (see [[izumi.distage.bootstrap.BootstrapLocator]]),
    * to set activation choices, pass `Activation` to [[izumi.distage.model.Planner#plan]] or [[izumi.distage.model.PlannerInput]].
    *
    * @param activation A map of axes of configuration to choices along these axes
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def withBootstrapActivation[F[_]: QuasiEffect: TagK: DefaultModule](activation: Activation, overrides: BootstrapModule*): Injector[F]

  /**
    * Create a new Injector from a custom [[izumi.distage.model.definition.BootstrapContextModule]].
    * The passed activation will affect _only_ the bootstrapping of the injector itself (see [[izumi.distage.bootstrap.BootstrapLocator]]),
    * to set activation choices, pass `Activation` to [[izumi.distage.model.Planner#plan]] or [[izumi.distage.model.PlannerInput]].
    *
    * @param activation A map of axes of configuration to choices along these axes
    * @param bootstrapBase See [[izumi.distage.bootstrap.BootstrapLocator.defaultBootstrap]]
    * @param overrides     Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                      They can be used to extend the Injector, e.g. add ability to inject config values
    */
  def withBootstrapActivation[F[_]: QuasiEffect: TagK: DefaultModule](
    activation: Activation,
    bootstrapBase: BootstrapContextModule,
    overrides: BootstrapModule*
  ): Injector[F]

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from results of a previous Injector's run
    *
    * @param parent Instances from parent [[izumi.distage.model.Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  def inherit[F[_]: QuasiEffect: TagK](parent: Locator): Injector[F]

  def inheritWithDefaultModule[F[_]: QuasiEffect: TagK](parent: Locator, defaultModule: Module): Injector[F]

  def bootloader[F[_]](
    input: PlannerInput,
    activation: Activation,
    bootstrapModule: BootstrapModule,
    defaultModule: DefaultModule[F],
  ): Bootloader = {
    new Bootloader(bootstrapModule, activation, input, this, defaultModule.module)
  }
}
