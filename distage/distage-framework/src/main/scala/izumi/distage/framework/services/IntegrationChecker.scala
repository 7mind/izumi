package izumi.distage.framework.services

import distage.{SafeType, TagK}
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.model.Locator
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectAsync}
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.logstage.api.IzLogger

import scala.annotation.nowarn
import scala.util.control.NonFatal

trait IntegrationChecker[F[_]] {
  def collectFailures(plan: OrderedPlan, integrationLocator: Locator): F[Option[NonEmptyList[ResourceCheck.Failure]]]
  final def checkOrFail(plan: OrderedPlan, integrationLocator: Locator): F[Unit] = {
    implicit val F: DIEffect[F] = integrationLocator.get[DIEffect[F]]
    collectFailures(plan, integrationLocator).flatMap {
      case Some(failures) =>
        F.fail(new IntegrationCheckException(failures))
      case None =>
        F.unit
    }
  }

  protected[this] implicit def tag: TagK[F]
}

object IntegrationChecker {

  class Impl[F[_]](
    logger: IzLogger
  )(implicit override protected val tag: TagK[F]
  ) extends IntegrationChecker[F] {

    @nowarn("msg=Unused import")
    override def collectFailures(plan: OrderedPlan, integrationLocator: Locator): F[Option[NonEmptyList[ResourceCheck.Failure]]] = {
      import scala.collection.compat._

      val steps = plan.steps.filter(op => plan.declaredRoots.contains(op.target)).toSet
      if (steps.nonEmpty) {
        logger.info(s"Going to check availability of ${steps.size -> "resources"} ${steps.map(_.target).niceList() -> "resourceList"}")
      }

      implicit val F: DIEffect[F] = integrationLocator.get[DIEffect[F]]
      implicit val P: DIEffectAsync[F] = integrationLocator.get[DIEffectAsync[F]]

      val (identityInstancesSet, fInstancesSet) = {
        steps.partitionMap {
          ick =>
            val idTpe = SafeType.get[IntegrationCheck[Identity]]
            if (ick.instanceType <:< idTpe) {
              println("i" -> ick.target -> ick.instanceType)
              Left(ick -> integrationLocator.lookupInstance[Any](ick.target).map(_.asInstanceOf[IntegrationCheck[Identity]]))
            } else {
              println("f" -> ick.target -> ick.instanceType)
              Right(ick -> integrationLocator.lookupInstance[Any](ick.target).map(_.asInstanceOf[IntegrationCheck[F]]))
            }
        }
      }

      val identityChecks = identityInstancesSet.collect { case (_, Some(ic)) => ic }
      val fChecks = fInstancesSet.collect { case (_, Some(ic)) => ic }
      val bad = (identityInstancesSet ++ fInstancesSet).collect { case (ick, None) => ick }

      if (bad.isEmpty) {
        for {
          identityChecked <- P.parTraverse(identityChecks)(i => checkWrap(F.maybeSuspend(runCheck[Identity](i)), i.toString))
          fChecked <- P.parTraverse(fChecks)(i => checkWrap(runCheck[F](i), i.toString))
          results = identityChecked ++ fChecked
          res = NonEmptyList.from(results.collect { case Left(failure) => failure })
        } yield res
      } else {
        F.fail(new DIAppBootstrapException(s"Inconsistent locator state: integration components ${bad.mkString("{", ", ", "}")} are missing from locator"))
      }
    }

    private def runCheck[F1[_]](resource: IntegrationCheck[F1])(implicit F1: DIEffect[F1]): F1[Either[ResourceCheck.Failure, Unit]] = {
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
