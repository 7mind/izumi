package com.github.pshirshov.izumi.distage.roles.role2.services

import com.github.pshirshov
import com.github.pshirshov.izumi.distage.app.DiAppBootstrapException
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.roles.IntegrationCheck
import com.github.pshirshov.izumi.distage.roles.launcher.exceptions.IntegrationCheckException
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.util.control.NonFatal

trait IntegrationChecker {
  def check(integrationComponents: Set[pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey], integrationLocator: Locator): Unit
}

class IntegrationCheckerImpl(logger: IzLogger) extends IntegrationChecker {
  def check(integrationComponents: Set[pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey], integrationLocator: Locator): Unit = {
    val ics = integrationComponents.map {
      ick =>
        integrationLocator.lookup[IntegrationCheck](ick) match {
          case Some(ic) =>
            ic.value
          case None =>
            throw new DiAppBootstrapException(s"Inconsistent locator state: integration component $ick is missing from plan")
        }
    }
    checkIntegrations(ics)
  }

  private def checkIntegrations(integrations: Set[IntegrationCheck]): Unit = {
    val checks = failingIntegrations(integrations)
    checks.fold(()) {
      failures =>
        throw new IntegrationCheckException(s"Integration check failed, failures were: ${failures.niceList()}", failures)
    }
  }

  private def failingIntegrations(integrations: Set[IntegrationCheck]): Option[Seq[ResourceCheck.Failure]] = {
    logger.info(s"Going to check availability of ${integrations.size -> "resources"}")

    val failures = integrations.toSeq.flatMap {
      resource =>
        logger.debug(s"Checking $resource")
        try {
          resource.resourcesAvailable() match {
            case failure@ResourceCheck.ResourceUnavailable(description, Some(cause)) =>
              logger.debug(s"Integration check failed: $resource unavailable: $description, $cause")
              Some(failure)
            case failure@ResourceCheck.ResourceUnavailable(description, None) =>
              logger.debug(s"Integration check failed: $resource unavailable: $description")
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
