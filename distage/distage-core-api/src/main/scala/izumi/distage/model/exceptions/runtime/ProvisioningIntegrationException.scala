package izumi.distage.model.exceptions.runtime

import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.ResourceCheck

object ProvisioningIntegrationException {
  def unapply(arg: ProvisioningException): Option[NonEmptyList[ResourceCheck.Failure]] = {
    NonEmptyList.from(arg.getSuppressed.iterator.collect { case i: IntegrationCheckException => i.failures.toList }.flatten.toList)
  }
}
