package izumi.distage.framework.services

import distage.{DIKey, TagK}
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.model.Locator
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.logstage.api.IzLogger

import scala.util.control.NonFatal

trait IntegrationChecker[F[_]] {
  def collectFailures(integrationComponents: Set[DIKey], integrationLocator: Locator): F[Either[Seq[ResourceCheck.Failure], Unit]]
  final def checkOrFail(integrationComponents: Set[DIKey], integrationLocator: Locator): F[Unit] = {
    implicit val F: DIEffect[F] = integrationLocator.get[DIEffect[F]]

    collectFailures(integrationComponents, integrationLocator).flatMap {
      case Left(failures) =>
        F.fail(new IntegrationCheckException(failures))
      case Right(_) =>
        F.unit
    }
  }

  protected[this] implicit def tag: TagK[F]
}

object IntegrationChecker {

  class Impl[F[_]](
    logger: IzLogger
  )(implicit protected val tag: TagK[F]
  ) extends IntegrationChecker[F] {

    override def collectFailures(integrationComponents: Set[DIKey], integrationLocator: Locator): F[Either[Seq[ResourceCheck.Failure], Unit]] = {
      if (integrationComponents.nonEmpty) {
        logger.info(s"Going to check availability of ${integrationComponents.size -> "resources"} ${integrationComponents -> "resourceList"}")
      }

      implicit val F: DIEffect[F] = integrationLocator.get[DIEffect[F]]
      implicit val P: DIEffectAsync[F] = integrationLocator.get[DIEffectAsync[F]]

      val instances = integrationComponents
        .toList.map {
          ick =>
            ick -> integrationLocator.lookupInstance[Any](ick).map(_.asInstanceOf[IntegrationCheck])
        }.toSet

      val good = instances.collect { case (_, Some(ic)) => ic }
      val bad = instances.collect { case (ick, None) => ick }

      if (bad.isEmpty) {
        P.parTraverse(good)(runCheck)
          .flatMap {
            results =>
              results.collect { case Left(failure) => failure } match {
                case Nil =>
                  F.pure(Right(()))
                case failures =>
                  F.pure(Left(failures))
              }
          }
      } else {
        F.fail(new DIAppBootstrapException(s"Inconsistent locator state: integration components ${bad.mkString("{", ", ", "}")} are missing from locator"))
      }
    }

    private def runCheck(resource: IntegrationCheck)(implicit F: DIEffect[F]): F[Either[ResourceCheck.Failure, Unit]] = {
      F.maybeSuspend {
        logger.debug(s"Checking $resource")
        try {
          resource.resourcesAvailable() match {
            case failure @ ResourceCheck.ResourceUnavailable(reason, Some(cause)) =>
              logger.debug(s"Integration check failed, $resource unavailable: $reason, $cause")
              Left(failure)
            case failure @ ResourceCheck.ResourceUnavailable(reason, None) =>
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
