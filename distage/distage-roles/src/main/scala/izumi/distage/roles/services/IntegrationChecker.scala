package izumi.distage.roles.services

import distage.{DIKey, TagK}
import izumi.distage.model.Locator
import izumi.distage.model.exceptions.DIException
import izumi.distage.model.monadic.{DIEffect, DIEffectAsync}
import izumi.distage.roles.model.{DiAppBootstrapException, IntegrationCheck}
import izumi.distage.roles.services.IntegrationChecker.IntegrationCheckException
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger
import DIEffect.syntax._

import scala.util.control.NonFatal

trait IntegrationChecker[F[_]] {
  def collectFailures(integrationComponents: Set[DIKey], integrationLocator: Locator): F[Either[Seq[ResourceCheck.Failure], Unit]]

  protected implicit val tag: TagK[F]
  final def checkOrFail(integrationComponents: Set[DIKey], integrationLocator: Locator): F[Unit] = {
    implicit val F: DIEffect[F] = integrationLocator.get[DIEffect[F]]

    collectFailures(integrationComponents, integrationLocator).flatMap {
      case Left(failures) =>
        F.fail(new IntegrationCheckException(s"Integration check failed, failures were: ${failures.niceList()}", failures))
      case Right(_) =>
        F.unit
    }
  }
}

object IntegrationChecker {

  class IntegrationCheckException(message: String, val failures: Seq[ResourceCheck.Failure]) extends DIException(message, null)

  class Impl[F[_] : TagK](
                           logger: IzLogger,
                         ) extends IntegrationChecker[F] {


    override protected val tag: TagK[F] = implicitly[TagK[F]]

    override def collectFailures(integrationComponents: Set[DIKey], integrationLocator: Locator): F[Either[Seq[ResourceCheck.Failure], Unit]] = {
      logger.info(s"Going to check availability of ${integrationComponents.size -> "resources"}")

      implicit val F: DIEffect[F] = integrationLocator.get[DIEffect[F]]
      implicit val P: DIEffectAsync[F] = integrationLocator.get[DIEffectAsync[F]]

      val instances = integrationComponents.toList.map {
        ick =>
          ick -> integrationLocator.lookupInstance[Any](ick).map(_.asInstanceOf[IntegrationCheck])
      }.toSet

      val good = instances.collect { case (_, Some(ic)) => ic }
      val bad = instances.collect { case (ick, None) => ick }

      if (bad.isEmpty) {
        P
          .parTraverse[IntegrationCheck, Either[ResourceCheck.Failure, Unit]](good)(runCheck)
          .flatMap {
            results =>
              results.collect({ case Left(failure) => failure }) match {
                case Nil =>
                  F.pure(Right(()))
                case failures =>
                  F.pure(Left(failures))
              }
          }
      } else {
        F.fail(new DiAppBootstrapException(s"Inconsistent locator state: integration components ${bad.mkString("{", ", ", "}")} are missing from locator"))
      }
    }

    private def runCheck(resource: IntegrationCheck)(implicit F: DIEffect[F]): F[Either[ResourceCheck.Failure, Unit]] = {
      F.maybeSuspend {
        logger.debug(s"Checking $resource")
        try {
          resource.resourcesAvailable() match {
            case failure@ResourceCheck.ResourceUnavailable(reason, Some(cause)) =>
              logger.debug(s"Integration check failed, $resource unavailable: $reason, $cause")
              Left(failure)
            case failure@ResourceCheck.ResourceUnavailable(reason, None) =>
              logger.debug(s"Integration check failed, $resource unavailable: $reason")
              Left(failure)
            case ResourceCheck.Success() =>
              Right(())
          }
        } catch {
          case NonFatal(exception) =>
            logger.error(s"Integration check for $resource threw $exception")
            Left(ResourceCheck.ResourceUnavailable(exception.getMessage, Some(exception)))
        }
      }
    }
  }

}
