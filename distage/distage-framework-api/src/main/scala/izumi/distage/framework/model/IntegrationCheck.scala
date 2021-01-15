package izumi.distage.framework.model

import izumi.fundamentals.platform.integration.ResourceCheck

/** A trait designating a component depending on an external, possibly unavailable resource.
  *
  * In [[izumi.distage.roles.RoleAppLauncher]], if `resourcesAvailable()` method returns a [[izumi.fundamentals.platform.integration.ResourceCheck.Failure]],
  * startup should stop. When used with [[izumi.distage.testkit.scalatest.DistageSpecScalatest]],
  * if `resourcesAvailable()` method returns a [[izumi.fundamentals.platform.integration.ResourceCheck.Failure]] the test will be skipped.
  */
trait IntegrationCheck[+F[_]] {
  /** This method must never throw, on error return [[izumi.fundamentals.platform.integration.ResourceCheck.Failure]] instead * */
  def resourcesAvailable(): F[ResourceCheck]
}
