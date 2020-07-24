package izumi.distage.framework.services

import distage.{DIKey, TagK}
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.model.Locator
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.strings.IzString.toRichIterable
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
        logger.info(s"Going to check availability of ${integrationComponents.size -> "resources"}: ${integrationComponents.niceList() -> "resourceList"}")
      }

      implicit val F: DIEffect[F] = integrationLocator.get[DIEffect[F]]
      implicit val P: DIEffectAsync[F] = integrationLocator.get[DIEffectAsync[F]]

      val (identityInstances, fInstances) = integrationComponents
        .toList.partitionMap {
          ick =>
            if (ick.tpe <:< DIKey[IntegrationCheck[Identity]].tpe) {
              Left(ick -> integrationLocator.lookupInstance[Any](ick).map(_.asInstanceOf[IntegrationCheck[Identity]]))
            } else {
              Right(ick -> integrationLocator.lookupInstance[Any](ick).map(_.asInstanceOf[IntegrationCheck[F]]))
            }
        }

      val identityInstancesSet = identityInstances.toSet
      val fInstancesSet = fInstances.toSet

      val identityChecks = identityInstancesSet.collect { case (_, Some(ic)) => ic }
      val fChecks = fInstancesSet.collect { case (_, Some(ic)) => ic }
      val bad = (identityInstancesSet ++ fInstancesSet).collect { case (ick, None) => ick }

      if (bad.isEmpty) {
        for {
          identityChecked <- P.parTraverse(identityChecks)(i => checkWrap(F.maybeSuspend(runCheck(i)), i.toString))
          fChecked <- P.parTraverse(fChecks)(i => checkWrap(runCheck(i), i.toString))
          results = identityChecked ++ fChecked
          res <- results.collect { case Left(failure) => failure } match {
            case Nil =>
              F.pure(Right(()))
            case failures =>
              F.pure(Left(failures))
          }
        } yield res
      } else {
        F.fail(new DIAppBootstrapException(s"Inconsistent locator state: integration components ${bad.mkString("{", ", ", "}")} are missing from locator"))
      }
    }

    private def runCheck[F1[_]](resource: IntegrationCheck[F1])(implicit F1: DIEffect[F1]): F1[Either[ResourceCheck.Failure, Unit]] = {
      resource.resourcesAvailable().map {
        case failure @ ResourceCheck.ResourceUnavailable(reason, Some(cause)) =>
          logger.debug(s"Integration check failed, $resource unavailable: $reason, $cause")
          Left(failure): Either[ResourceCheck.Failure, Unit]
        case failure @ ResourceCheck.ResourceUnavailable(reason, None) =>
          logger.debug(s"Integration check failed, $resource unavailable: $reason")
          Left(failure)
        case ResourceCheck.Success() =>
          Right(())
      }
    }

    private def checkWrap(
      wrap: F[Either[ResourceCheck.Failure, Unit]],
      resource: String,
    )(implicit F: DIEffect[F]
    ): F[Either[ResourceCheck.Failure, Unit]] = {
      logger.debug(s"Checking $resource")
      F.definitelyRecover(wrap) {
        case NonFatal(exception) =>
          logger.error(s"Integration check for $resource threw $exception")
          F.pure(Left(ResourceCheck.ResourceUnavailable(exception.getMessage, Some(exception))))
        case other =>
          F.fail(other)
      }
    }
  }

}
