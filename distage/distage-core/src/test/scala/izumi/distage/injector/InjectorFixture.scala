package izumi.distage.injector

import distage.{Injector, Locator, PlannerInput}
import izumi.fundamentals.platform.functional.Identity

trait InjectorFixture {
  protected final def produceLocator(injector: => Injector[Identity])(input: PlannerInput): Locator =
    injector.produce(input).unsafeGet()
}
