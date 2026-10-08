package izumi.distage.injector

import distage.{Injector, Locator, PlannerInput, Tag}
import izumi.fundamentals.platform.functional.Identity

trait InjectorFixture {
  protected final def produceLocator(injector: => Injector[Identity])(input: PlannerInput): Locator =
    injector.produce(input).unsafeGet()

  protected final def producePlannedLocator(injector: Injector[Identity])(input: PlannerInput): Locator =
    injector.produce(injector.planUnsafe(input)).unsafeGet()

  protected final def produceInstance[A: Tag](injector: => Injector[Identity])(input: PlannerInput): A =
    produceLocator(injector)(input).get[A]
}
