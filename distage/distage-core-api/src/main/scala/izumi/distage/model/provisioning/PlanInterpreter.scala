package izumi.distage.model.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.*
import izumi.distage.model.exceptions.interpretation.{IncompatibleEffectTypesException, MissingImport, MissingProxyAdapterException, ProvisioningException, UnexpectedDIException, UnexpectedProvisionResultException, UnsupportedProxyOpException}
import izumi.distage.model.plan.DIPlan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.reflection.*
import izumi.fundamentals.platform.exceptions.IzThrowable
import izumi.fundamentals.platform.strings.IzString.*
import izumi.reflect.TagK

trait PlanInterpreter {
  def run[F[_]: TagK: QuasiIO](
    plan: DIPlan,
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
    plan: DIPlan,
    parentContext: Locator,
    failure: ProvisioningFailure,
    meta: FailedProvisionMeta,
    fullStackTraces: Boolean,
  ) {
    def throwException[A]()(implicit F: QuasiIO[F]): F[A] = {
      val repr = failure match {
        case ProvisioningFailure.AggregateFailure(_, failures, _) =>
          val messages = failures
            .map {
              case UnexpectedDIException(op, problem) =>
                import IzThrowable.*
                s"DISTAGE BUG: exception while processing $op; please report: https://github.com/7mind/izumi/issues\n${problem.stackTrace}"
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
            }
            .niceMultilineList("[!]")
          s"Plan interpreter failed:\n$messages"
        case ProvisioningFailure.BrokenGraph(matrix, _) =>
          s"DISTAGE BUG: cannot compute next operations to process; please report: https://github.com/7mind/izumi/issues\n${matrix.links
            .map { case (k, v) => s"$k: $v" }.niceList()}"
        case ProvisioningFailure.CantBuildIntegrationSubplan(errors, _) =>
          s"Unable to build integration checks subplan:\n${errors.map(DIError.format(plan.input.activation))}"
      }

      val ccFailed = failure.status
        .collect {
          case (key, _: OpStatus.Failure) =>
            key
        }.toSet.size
      val ccDone = failure.status
        .collect {
          case (key, _: OpStatus.Success) =>
            key
        }.toSet.size
      val ccPending = failure.status
        .collect {
          case (key, _: OpStatus.Planned) =>
            key
        }.toSet.size
      val ccTotal = failure.status.size

      import izumi.fundamentals.platform.exceptions.IzThrowable.*

      val exceptions = failure match {
        case f: ProvisioningFailure.AggregateFailure =>
          f.failures.flatMap {
            case e: UnexpectedDIException =>
              Seq(e.problem)
            case _ =>
              Seq.empty
          }
        case _: ProvisioningFailure.BrokenGraph =>
          Seq.empty
        case _: ProvisioningFailure.CantBuildIntegrationSubplan =>
          Seq.empty
      }

      F.fail {
        new ProvisioningException(
          s"""Interpreter stopped; out of $ccTotal operations: $ccFailed failed, $ccDone succeeded, $ccPending ignored
             |$repr
             |""".stripMargin,
          null,
        )
          .addAllSuppressed(exceptions)
      }
    }
  }

  object FailedProvision {
    implicit final class FailedProvisionExt[F[_]](private val p: Either[FailedProvision[F], Locator]) extends AnyVal {
      def throwOnFailure()(implicit F: QuasiIO[F]): F[Locator] = p.fold(_.throwException(), F.pure)
    }
  }

}
