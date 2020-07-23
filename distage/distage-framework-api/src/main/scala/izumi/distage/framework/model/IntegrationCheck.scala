package izumi.distage.framework.model

import izumi.fundamentals.platform.integration.ResourceCheck

/** A trait designating a component depending on an external, possibly unavailable resource.
  *
  * In [[RoleStarter]], if `resourcesAvailable()` method returns a [[ResourceCheck.Failure]],
  * startup should stop. When used with [[izumi.distage.testkit.DistageSpec]],
  * if `resourcesAvailable()` method returns a [[ResourceCheck.Failure]] the test will be skipped.
  *
  * Not wrapped in `F` because you shouldn't use async or any non-trivial effect inside IntegrationCheck,
  * it must be _trivial_ and return near-instantly. The execution will be suspended in F.
  */
trait IntegrationCheck[F[_]] {
  /** This method must never throw, on error return [[ResourceCheck.Failure]] instead * */
  def resourcesAvailable(): F[ResourceCheck]
}
