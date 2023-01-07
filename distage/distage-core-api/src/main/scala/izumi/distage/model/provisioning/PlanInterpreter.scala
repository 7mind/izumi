package izumi.distage.model.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.*
import izumi.distage.model.exceptions.interpretation.*
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue.{IncompatibleEffectTypesException, MissingImport, MissingProxyAdapterException, ProvisionerExceptionIssue, UnexpectedProvisionResultException, UninitializedDependency, UnsupportedProxyOpException}
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue.ProvisionerExceptionIssue.{IntegrationCheckFailure, UnexpectedIntegrationCheckException, UnexpectedStepProvisioningException}
import izumi.distage.model.plan.Plan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.reflection.*
import izumi.fundamentals.platform.IzumiProject
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import izumi.fundamentals.platform.strings.IzString.*
import izumi.reflect.TagK

trait PlanInterpreter {
  def run[F[_]: TagK: QuasiIO](
    plan: Plan,
    parentLocator: Locator,
    filterFinalizers: FinalizerFilter[F],
  ): Lifecycle[F, Either[FailedProvision[F], Locator]]
}

object PlanInterpreter {
  trait FinalizerFilter[F[_]] {
    def filter(finalizers: collection.Seq[Finalizer[F]]): collection.Seq[Finalizer[F]]
  }
  object FinalizerFilter {
    def all[F[_]]: FinalizerFilter[F] = identity
  }

  final case class Finalizer[+F[_]](key: DIKey, effect: () => F[Unit], fType: SafeType)
  object Finalizer {
    def apply[F[_]: TagK](key: DIKey, effect: () => F[Unit]): Finalizer[F] = {
      new Finalizer(key, effect, SafeType.getK[F])
    }
  }

  case class FailedProvisionMeta(status: Map[DIKey, OpStatus])

  final case class FailedProvision[F[_]](
    failed: ProvisionImmutable[F],
    plan: Plan,
    parentContext: Locator,
    failure: ProvisioningFailure,
    meta: FailedProvisionMeta,
    fullStackTraces: Boolean,
  ) {
    def throwException[A]()(implicit F: QuasiIO[F]): F[A] = {
      val repr = failure match {
        case ProvisioningFailure.AggregateFailure(_, failures, _) =>
          def stackTrace(exception: Throwable): String = {
            if (fullStackTraces) exception.stackTrace else exception.getMessage
          }
          val messages = failures
            .map {
              case UnexpectedStepProvisioningException(op, problem) =>
                val excName = problem match {
                  case di: DIException => di.getClass.getSimpleName
                  case o => o.getClass.getName
                }
                s"Got exception when trying to to execute $op, exception was:\n$excName:\n${stackTrace(problem)}"

              case IntegrationCheckFailure(key, problem) =>
                s"Integration check failed for $key, exception was:\n${stackTrace(problem)}"

              case UnexpectedIntegrationCheckException(key, problem) =>
                IzumiProject.bugReportPrompt(s"unexpected exception while processing integration check for $key", stackTrace(problem))

              case MissingImport(op) =>
                MissingInstanceException.format(op.target, op.references)

              case IncompatibleEffectTypesException(op, provisionerEffectType, actionEffectType) =>
                IncompatibleEffectTypesException.format(op, provisionerEffectType, actionEffectType)

              case UnexpectedProvisionResultException(key, results) =>
                s"Unexpected operation result for $key: $results, expected a single NewInstance!"

              case MissingProxyAdapterException(key, op) =>
                s"Cannot get dispatcher $key for $op"

              case UnsupportedProxyOpException(op) =>
                s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op"
              case UninitializedDependency(key, parameters) =>
                IzumiProject.bugReportPrompt(
                  s" Tried to instantiate class, but some dependences were uninitialized: Class: $key, dependencies: ${parameters.mkString(",")}"
                )

            }
            .niceMultilineList("[!]")
          s"Plan interpreter failed:\n$messages"

        case ProvisioningFailure.BrokenGraph(matrix, _) =>
          IzumiProject.bugReportPrompt(
            "Cannot compute next operations to process",
            matrix.links
              .map { case (k, v) => s"$k: $v" }.niceList(),
          )

        case ProvisioningFailure.CantBuildIntegrationSubplan(errors, _) =>
          s"Unable to build integration checks subplan:\n${errors.map(DIError.format(plan.input.activation))}"
      }

      val ccFailed = failure.status.collect { case (key, _: OpStatus.Failure) => key }.toSet.size
      val ccDone = failure.status.collect { case (key, _: OpStatus.Success) => key }.toSet.size
      val ccPending = failure.status.collect { case (key, _: OpStatus.Planned) => key }.toSet.size
      val ccTotal = failure.status.size

      val allExceptions = failure match {
        case f: ProvisioningFailure.AggregateFailure =>
          f.failures.collect {
            case e: ProvisionerExceptionIssue => e.problem
          }
        case _: ProvisioningFailure.BrokenGraph => Seq.empty
        case _: ProvisioningFailure.CantBuildIntegrationSubplan => Seq.empty
      }

      F.fail {
        new ProvisioningException(
          s"""Interpreter stopped; out of $ccTotal operations: $ccFailed failed, $ccDone succeeded, $ccPending ignored
             |$repr
             |""".stripMargin,
          null,
        ).addAllSuppressed(allExceptions)
      }
    }
  }

  object FailedProvision {
    implicit final class FailedProvisionExt[F[_]](private val p: Either[FailedProvision[F], Locator]) extends AnyVal {
      def throwOnFailure()(implicit F: QuasiIO[F]): F[Locator] = p.fold(_.throwException(), F.pure)
    }
  }

}
