package izumi.distage.framework.services

import distage.{DIKey, TagK}
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.model.Locator
import izumi.distage.model.effect.QuasiIO.syntax._
import izumi.distage.model.effect.{QuasiAsync, QuasiIO}
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.logstage.api.IzLogger

import scala.annotation.nowarn
import scala.util.control.NonFatal

trait IntegrationChecker[F[_]] {
  def collectFailures(identityIntegrations: Set[DIKey], effectIntegrations: Set[DIKey], integrationLocator: Locator): F[Option[NonEmptyList[ResourceCheck.Failure]]]

  final def checkOrFail(identityIntegrations: Set[DIKey], effectIntegrations: Set[DIKey], integrationLocator: Locator)(implicit F: QuasiIO[F]): F[Unit] = {
    collectFailures(identityIntegrations, effectIntegrations, integrationLocator).flatMap {
      case Some(failures) =>
        F.fail(new IntegrationCheckException(failures))
      case None =>
        F.unit
    }
  }
}

object IntegrationChecker {

  class Impl[F[_]: TagK](
    logger: IzLogger
  ) extends IntegrationChecker[F] {

    @nowarn("msg=Unused import")
    override def collectFailures(
      identityIntegrations: Set[DIKey],
      effectIntegrations: Set[DIKey],
      integrationLocator: Locator,
    ): F[Option[NonEmptyList[ResourceCheck.Failure]]] = {
      import scala.collection.compat._

      if (identityIntegrations.nonEmpty || effectIntegrations.nonEmpty) {
        logger.info(
          s"Going to check availability of ${(identityIntegrations.size + effectIntegrations.size) -> "resources"} ${(identityIntegrations ++ effectIntegrations).niceList() -> "resourceList"}"
        )
      }
      implicit val F: QuasiIO[F] = integrationLocator.get[QuasiIO[F]]
      implicit val P: QuasiAsync[F] = integrationLocator.get[QuasiAsync[F]]

      def retrieveChecks[A](keys: Set[DIKey]): (Set[DIKey], Set[A]) = {
        keys.partitionMap(k => integrationLocator.lookupInstance[Any](k).asInstanceOf[Option[A]].toRight(k))
      }
      val (identityBad, identityChecks) = retrieveChecks[IntegrationCheck[Identity]](identityIntegrations)
      val (effectBad, effectChecks) = retrieveChecks[IntegrationCheck[F]](effectIntegrations)

      if (identityBad.isEmpty || effectBad.isEmpty) {
        for {
          identityChecked <- P.parTraverse(identityChecks)(i => checkWrap(i)(F.maybeSuspend(runCheck[Identity](i))))
          effectChecked <- P.parTraverse(effectChecks)(i => checkWrap(i)(runCheck[F](i)))
          results = identityChecked ++ effectChecked
          errors = NonEmptyList.from(results.collect { case Left(failure) => failure })
        } yield errors
      } else {
        F.fail(
          new DIAppBootstrapException(
            s"Inconsistent locator state: integration components ${(identityBad.iterator ++ effectBad.iterator).mkString("{", ", ", "}")} are missing from locator"
          )
        )
      }
    }

    private def runCheck[F1[_]](resource: IntegrationCheck[F1])(implicit F1: QuasiIO[F1]): F1[Either[ResourceCheck.Failure, Unit]] = {
      resource.resourcesAvailable().map {
        case failure @ ResourceCheck.ResourceUnavailable(reason, Some(cause)) =>
          logger.debug(s"Integration check failed, $resource unavailable: $reason, $cause")
          Left(failure)
        case failure @ ResourceCheck.ResourceUnavailable(reason, None) =>
          logger.debug(s"Integration check failed, $resource unavailable: $reason")
          Left(failure)
        case ResourceCheck.Success() =>
          Right(())
      }
    }

    private def checkWrap[F1[_]](
      resource: IntegrationCheck[F1]
    )(wrap: => F[Either[ResourceCheck.Failure, Unit]]
    )(implicit F: QuasiIO[F]
    ): F[Either[ResourceCheck.Failure, Unit]] = {
      logger.debug(s"Checking $resource")
      F.definitelyRecover(wrap) {
        case NonFatal(exception) =>
          F.maybeSuspend {
            logger.error(s"Integration check for $resource threw $exception")
            Left(ResourceCheck.ResourceUnavailable(exception.getMessage, Some(exception)))
          }
        case other =>
          F.fail(other)
      }
    }
  }

}
