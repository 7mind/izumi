package izumi.distage.model.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.definition.errors.ProvisionerIssue.*
import izumi.distage.model.definition.errors.ProvisionerIssue.ProvisionerExceptionIssue.*
import izumi.distage.model.exceptions.*
import izumi.distage.model.exceptions.runtime.{MissingInstanceException, ProvisioningException}
import izumi.distage.model.plan.Plan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.provisioning.Provision.{ProvisionImmutable, ProvisionInstances}
import izumi.distage.model.reflection.*
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.IzumiProject
import izumi.fundamentals.platform.build.MacroParameters
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import izumi.fundamentals.platform.strings.IzString.*
import izumi.reflect.TagK

trait PlanInterpreter {
  def run[F[_]: TagK: QuasiIO](
    plan: Plan,
    parentLocator: Locator,
    filterFinalizers: FinalizerFilter[F],
  ): Lifecycle[F, Either[FailedProvision, Locator]]
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

  final case class FailedProvisionMeta(status: Map[DIKey, OpStatus])

  final case class FailedProvisionInternal[F[_]](provision: ProvisionImmutable[F], fail: FailedProvision)

  final case class FailedProvision(
    failed: ProvisionInstances,
    plan: Plan,
    parentContext: Locator,
    failure: ProvisioningFailure,
    meta: FailedProvisionMeta,
    fullStackTraces: Boolean,
  ) {
    def toThrowable: Throwable = {
      import FailedProvision.ProvisioningFailureOps
      val repr = failure.render(fullStackTraces)
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

      new ProvisioningException(
        s"""Interpreter stopped; out of $ccTotal operations: $ccFailed failed, $ccDone succeeded, $ccPending ignored
           |$repr
           |""".stripMargin,
        null,
      ).addAllSuppressed(allExceptions)
    }
  }

  object FailedProvision {
    implicit final class FailedProvisionExt[F[_]](private val p: Either[FailedProvision, Locator]) extends AnyVal {
      /** @throws ProvisioningException in `F` effect type */
      def failOnFailure()(implicit F: QuasiIO[F]): F[Locator] = p.fold(f => F.fail(f.toThrowable), F.pure)
      def throwOnFailure(): Locator = p match {
        case Left(f) =>
          throw f.toThrowable
        case Right(value) =>
          value
      }
    }

    implicit class ProvisioningFailureOps(private val failure: ProvisioningFailure) extends AnyVal {
      def render(fullStackTraces: Boolean): String = {
        failure match {
          case ProvisioningFailure.AggregateFailure(_, failures, _) =>
            def stackTrace(exception: Throwable): String = {
              if (fullStackTraces) exception.stackTrace else exception.getMessage
            }

            val messages = failures
              .map {
                case UnexpectedStepProvisioning(op, problem) =>
                  val excName = problem match {
                    case di: DIException => di.getClass.getSimpleName
                    case o => o.getClass.getName
                  }
                  s"Got exception when trying to to execute $op, exception was:\n$excName:\n${stackTrace(problem)}"

                case IntegrationCheckFailure(key, problem) =>
                  s"Integration check failed for $key, exception was:\n${stackTrace(problem)}"

                case UnexpectedIntegrationCheck(key, problem) =>
                  IzumiProject.bugReportPrompt(s"unexpected exception while processing integration check for $key", stackTrace(problem))

                case MissingImport(op, similarSame, similarSub) =>
                  MissingInstanceException.format(op.target, op.references, similarSame, similarSub)

                case IncompatibleEffectTypes(op, provisionerEffectType, actionEffectType) =>
                  IncompatibleEffectTypes.format(op, provisionerEffectType, actionEffectType)

                case UnexpectedProvisionResult(key, results) =>
                  s"Unexpected operation result for $key: $results, expected a single NewInstance!"

                case MissingProxyAdapter(key, op) =>
                  s"Cannot get dispatcher $key for $op"

                case UnsupportedProxyOp(op) =>
                  s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op"
                case UninitializedDependency(key, parameters) =>
                  IzumiProject.bugReportPrompt(
                    s" Tried to instantiate class, but some dependences were uninitialized: Class: $key, dependencies: ${parameters.mkString(",")}"
                  )
                case IncompatibleEffectType(key, effect) =>
                  IzumiProject.bugReportPrompt(
                    s"Incompatible effect type in operation $key: $effect !<:< ${SafeType.identityEffectType}; this had to be handled before"
                  )
                case MissingRef(key, context, missing) =>
                  s"Failed to fetch keys while working on $key: ${missing.mkString(",")}, context: $context"
                case DuplicateInstances(key) =>
                  s"Cannot continue, key is already in the object graph: $key"

                case IncompatibleTypes(key, expected, got) =>
                  s"Dispatcher contains incompatible key: $key with type ${expected.tag}, found: $got"

                case IncompatibleRuntimeClass(key, got, clue) =>
                  s"Instance of type `$got` supposed to be assigned to incompatible key $key. Context: $clue"
                case MissingInstance(key) =>
                  s"Cannot find $key in the object graph"
                case UnsupportedOp(tpe, op, context) =>
                  s"Cannot make proxy for $tpe in ${op.target}: $context"
                case NoRuntimeClass(key) =>
                  s"Cannot build proxy for operation $key: runtime class is not available for ${key.tpe}"
                case ProxyProviderFailingImplCalled(key, value, cause) =>
                  cause match {
                    case ProxyFailureCause.CantFindStrategyClass(name) =>
                      s"""DynamicProxyProvider: couldn't create a cycle-breaking proxy - cyclic dependencies support is enabled, but proxy provider class is not on the classpath, couldn't instantiate `$name`.
                         |
                         |Please add dependency on `libraryDependencies += "io.7mind.izumi" %% "distage-core-proxy-bytebuddy" % "${MacroParameters
                          .artifactVersion()}"` to your build.
                         |
                         |failed op: $key""".stripMargin
                    case ProxyFailureCause.ProxiesDisabled() =>
                      s"ProxyProviderFailingImpl used: creation of cycle-breaking proxies is disabled, key $key, provider $value"
                  }
                case ProxyStrategyFailingImplCalled(key, value) =>
                  s"ProxyStrategyFailingImpl does not support proxies, key=$key, strategy=$value"
                case ProxyClassloadingFailed(context, causes) =>
                  s"Failed to load proxy class with ByteBuddy " +
                  s"class=${context.runtimeClass}, params=${context.params}\n\n" +
                  s"exception 1(DynamicProxyProvider classLoader)=${causes.head.stackTrace}\n\n" +
                  s"exception 2(classloader of class)=${causes.last.stackTrace}"
                case ProxyInstantiationFailed(context, cause) =>
                  s"Failed to instantiate class with ByteBuddy, make sure you don't dereference proxied parameters in constructors: " +
                  s"class=${context.runtimeClass}, params=${context.params}, exception=${cause.stackTrace}"
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
            s"Unable to build integration checks subplan:\n${errors.map(DIError.format)}"
        }
      }
    }
  }

}
