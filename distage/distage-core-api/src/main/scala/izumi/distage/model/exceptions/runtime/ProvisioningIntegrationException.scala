package izumi.distage.model.exceptions.runtime

import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.integration.ResourceCheck

object ProvisioningIntegrationException {
  def unapply(arg: ProvisioningException): Option[NEList[ResourceCheck.Failure]] = {
    NEList.from(arg.getSuppressed.iterator.collect { case i: IntegrationCheckException => i.failures.toList }.flatten.toList)
  }
}
