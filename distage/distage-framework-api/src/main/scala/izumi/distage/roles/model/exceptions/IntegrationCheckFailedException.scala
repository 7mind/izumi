package izumi.distage.roles.model.exceptions

import izumi.distage.model.exceptions.DIException
import izumi.fundamentals.platform.integration.ResourceCheck

final class IntegrationCheckFailedException(message: String, cause: Option[Throwable]) extends DIException(message, cause.orNull) {
  def toResourceCheck: ResourceCheck.ResourceUnavailable = ResourceCheck.ResourceUnavailable(message, cause)
}
