package com.github.pshirshov.izumi.distage.roles

import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck

/** A trait designating a component depending on an external, possibly unavailable resource.
  *
  * In [[RoleStarter]], if `resourcesAvailable()` method returns a [[ResourceCheck.Failure]],
  * startup should stop. When used with [[com.github.pshirshov.izumi.distage.testkit.DistageSpec]],
  * if `resourcesAvailable()` method returns a [[ResourceCheck.Failure]] the test will be skipped.
  **/
trait IntegrationCheck {
  /** This method must never throw, on error return [[ResourceCheck.Failure]] instead **/
  def resourcesAvailable(): ResourceCheck
}
