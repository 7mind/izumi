package izumi.distage.roles.services

import distage.DIKey
import izumi.distage.model.Locator
import izumi.distage.model.exceptions.DIException
import izumi.distage.roles.model.{DiAppBootstrapException, IntegrationCheck}
import izumi.distage.roles.services.IntegrationChecker.IntegrationCheckException
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

import scala.util.control.NonFatal

trait IntegrationChecker {
  def check(integrationComponents: Set[DIKey], integrationLocator: Locator): Option[Seq[ResourceCheck.Failure]]

  final def checkOrFail(integrationComponents: Set[DIKey], integrationLocator: Locator): Unit = {
    check(integrationComponents, integrationLocator).fold(()) {
      failures =>
        throw new IntegrationCheckException(s"Integration check failed, failures were: ${failures.niceList()}", failures)
    }
  }
}

object IntegrationChecker {
  class IntegrationCheckException(message: String, val failures: Seq[ResourceCheck.Failure]) extends DIException(message, null)

  class Impl(
              logger: IzLogger,
            ) extends IntegrationChecker {
    def check(integrationComponents: Set[DIKey], integrationLocator: Locator): Option[Seq[ResourceCheck.Failure]] = {
      val integrations = integrationComponents.map {
        ick =>
          integrationLocator.lookupInstance[Any](ick) match {
            case Some(ic) =>
              ic
            case None =>
              throw new DiAppBootstrapException(s"Inconsistent locator state: integration component $ick is missing from locator")
          }
      }
      failingIntegrations(integrations.asInstanceOf[Set[IntegrationCheck]])
    }

    private def failingIntegrations(integrations: Set[IntegrationCheck]): Option[Seq[ResourceCheck.Failure]] = {
      logger.info(s"Going to check availability of ${integrations.size -> "resources"}")

      val failures = integrations.toSeq.flatMap {
        resource =>
          logger.debug(s"Checking $resource")
          try {
            resource.resourcesAvailable() match {
              case failure@ResourceCheck.ResourceUnavailable(reason, Some(cause)) =>
                logger.debug(s"Integration check failed, $resource unavailable: $reason, $cause")
                Some(failure)
              case failure@ResourceCheck.ResourceUnavailable(reason, None) =>
                logger.debug(s"Integration check failed, $resource unavailable: $reason")
                Some(failure)
              case ResourceCheck.Success() =>
                None
            }
          } catch {
            case NonFatal(exception) =>
              logger.error(s"Integration check for $resource threw $exception")
              Some(ResourceCheck.ResourceUnavailable(exception.getMessage, Some(exception)))
          }
      }
      Some(failures).filter(_.nonEmpty)
    }
  }

}
