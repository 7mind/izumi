package izumi.distage.provisioning

import distage.Id
import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.Locator.LocatorMeta
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.exceptions.{ForwardRefException, IncompatibleEffectTypesException, ProvisionerIssue, SanityCheckFailedException}
import izumi.distage.model.plan.ExecutableOp.{MonadicOp, _}
import izumi.distage.model.plan.{DIPlan, ExecutableOp}
import izumi.distage.model.planning.PlanAnalyzer
import izumi.distage.model.provisioning.*
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FailedProvisionMeta, Finalizer, FinalizerFilter}
import izumi.distage.model.provisioning.strategies.*
import izumi.distage.model.reflection.*
import izumi.functional.IzEither.*
import izumi.fundamentals.graphs.ToposortError
import izumi.fundamentals.graphs.tools.{Toposort, ToposortLoopBreaker}
import izumi.reflect.TagK

import scala.annotation.nowarn
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object PlanInterpreterDefaultRuntimeImpl {
  import izumi.distage.model.plan.repr.*
  import izumi.distage.model.plan.topology.PlanTopology
  import izumi.distage.model.reflection.*
  import izumi.fundamentals.platform.strings.IzString.toRichString

  /**
    * Linearized graph which is ready to be consumed by linear executors
    *
    * May contain cyclic dependencies resolved with proxies
    */
  @deprecated("should be removed once we finish transition to parallel interpreter", "13/04/2021")
  private final case class OrderedPlan(
    steps: Vector[ExecutableOp],
    topology: PlanTopology,
  ) {
    private def keys: Set[DIKey] = {
      steps.map(_.target).toSet
    }

    /** Print while omitting package names for unambiguous types */
    override def toString: String = {
      val minimizer = KeyMinimizer(this.keys, DIRendering.colorsEnabled)
      val tf = TypeFormatter.minimized(minimizer)
      val kf = KeyFormatter.minimized(minimizer)
      val opFormatter = OpFormatter(kf, tf, DIRendering.colorsEnabled)

      this.steps.map(opFormatter.format).mkString("\n").listing()
    }
  }

}

// TODO: add introspection capabilities
class PlanInterpreterDefaultRuntimeImpl(
  executor: OperationExecutor,
  importStrategy: ImportStrategy,
  verifier: ProvisionOperationVerifier,
  fullStackTraces: Boolean @Id("izumi.distage.interpreter.full-stacktraces"),
  analyzer: PlanAnalyzer,
) extends PlanInterpreter {
  import PlanInterpreterDefaultRuntimeImpl.*

  type OperationMetadata = Long

  override def run[F[_]: TagK: QuasiIO](
    plan: DIPlan,
    parentLocator: Locator,
    filterFinalizers: FinalizerFilter[F],
  ): Lifecycle[F, Either[FailedProvision[F], Locator]] = {
    val sorted = {
      val ordered = Toposort.cycleBreaking(
        predecessors = plan.plan.predecessors,
        break = new ToposortLoopBreaker[DIKey] {
          override def onLoop(done: Seq[DIKey], loopMembers: Map[DIKey, Set[DIKey]]): Either[ToposortError[DIKey], ToposortLoopBreaker.ResolvedLoop[DIKey]] = {
            throw new SanityCheckFailedException(s"Integrity check failed: loops are not expected at this point, processed: $done, loops: $loopMembers")
          }
        },
      )

      val sortedKeys = ordered match {
        case Left(value) =>
          throw new SanityCheckFailedException(s"Toposort is not expected to fail here: $value")

        case Right(value) =>
          value
      }

      val sortedOps = sortedKeys.flatMap(plan.plan.meta.nodes.get).toVector
      val topology = analyzer.topology(plan.plan.meta.nodes.values)
      val finalPlan = OrderedPlan(sortedOps, topology)

      val reftable = analyzer.topologyFwdRefs(finalPlan.steps)
      if (reftable.dependees.matrix.links.nonEmpty) {
        throw new ForwardRefException(s"Cannot finish the plan, there are forward references: ${reftable.dependees.matrix.links.mkString("\n")}!", reftable)
      }
      finalPlan
    }
    instantiate[F](sorted, plan, parentLocator, filterFinalizers)
  }

  private[this] def instantiate[F[_]: TagK](
    plan: OrderedPlan,
    diplan: DIPlan,
    parentLocator: Locator,
    filterFinalizers: FinalizerFilter[F],
  )(implicit F: QuasiIO[F]
  ): Lifecycle[F, Either[FailedProvision[F], Locator]] = {
    Lifecycle.make(
      acquire = instantiateImpl(plan, diplan, parentLocator)
    )(release = {
      resource =>
        val finalizers = resource match {
          case Left(failedProvision) => failedProvision.failed.finalizers
          case Right(locator) => locator.finalizers
        }
        filterFinalizers.filter(finalizers).foldLeft(F.unit) {
          case (acc, f) => acc.guarantee(F.suspendF(f.effect()))
        }
    })
  }

  private[this] def instantiateImpl[F[_]: TagK](
    plan: OrderedPlan,
    diplan: DIPlan,
    parentContext: Locator,
  )(implicit F: QuasiIO[F]
  ): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
    val mutProvisioningContext = ProvisionMutable[F](diplan, parentContext)

    val mutExcluded = mutable.Set.empty[DIKey]
    val mutFailures = mutable.ArrayBuffer.empty[ProvisioningFailure]
    val meta = mutable.HashMap.empty[DIKey, OperationMetadata]

    def currentContext() = {
      LocatorContext(mutProvisioningContext.toImmutable, parentContext)
    }

    def processStep[T <: ExecutableOp, E](h: T => F[Either[E, Seq[NewObjectOp]]])(step: T): F[Either[E, Seq[NewObjectOp]]] = {
      for {
        before <- F.maybeSuspend(System.nanoTime())
        excludeOp <- F.maybeSuspend(mutExcluded.contains(step.target))
        r <- F.ifThenElse(excludeOp)(F.pure(Right(Seq.empty[NewObjectOp]): Either[E, Seq[NewObjectOp]]), h(step))
        after <- F.maybeSuspend(System.nanoTime())
      } yield {
        val time = after - before
        meta.put(step.target, time)
        r
      }
    }

    def doImport(step: ImportDependency): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
      F.maybeSuspend(importStrategy.importDependency(currentContext(), step))
    }

    val imports = new ArrayBuffer[ImportDependency]()
    val otherSteps = new ArrayBuffer[NonImportOp]()
    plan.steps.foreach {
      case i: ImportDependency =>
        imports.append(i)
      case o: NonImportOp =>
        otherSteps.append(o)
    }

    @nowarn("msg=Unused import")
    def makeMeta(): LocatorMeta = {
      LocatorMeta(meta.view.mapValues(Duration.fromNanos).toMap)
    }

    def doFail(immutable: Provision.ProvisionImmutable[F]): Either[FailedProvision[F], LocatorDefaultImpl[F]] = {
      Left(FailedProvision[F](immutable, diplan, parentContext, mutFailures.toVector, FailedProvisionMeta(makeMeta().timings), fullStackTraces))
    }

    def runSteps(otherSteps: ArrayBuffer[NonImportOp]): F[Unit] = {
      F.traverse_(otherSteps) {
        step =>
          processStep(executor.execute(currentContext(), _: NonImportOp))(step).flatMap {
            case Right(newObjectOps) =>
              F.maybeSuspend {
                newObjectOps.foreach {
                  newObject =>
                    Try(mutProvisioningContext.interpretResult(verifier, newObject)) match {
                      case Success(_) =>
                      case Failure(failure) =>
                        mutExcluded ++= plan.topology.transitiveDependees(step.target)
                        mutFailures += StepProvisioningFailure(step, failure)
                    }
                }
              }

            case Left(failure) =>
              F.maybeSuspend {
                mutExcluded ++= plan.topology.transitiveDependees(step.target)
                mutFailures += AggregateFailure(Seq(failure))
                ()
              }
          }
      }
    }

    for {
      // do imports first before everything
      importResults <- F.traverse(imports)(processStep(doImport)).map(_.biAggregateScalar)
      // verify effect type for everything else first before everything
      effectIssues <- verifyEffectType[F](otherSteps.toSeq)

      allIssues = importResults.left.getOrElse(List.empty) ++ effectIssues.left.getOrElse(List.empty)

      _ <- F.ifThenElse(allIssues.isEmpty)(
        F.unit,
        F.maybeSuspend {
          mutFailures += AggregateFailure(allIssues)
          ()
        },
      )

      _ <- F.maybeSuspend(importResults.toSeq.flatten.flatten.foreach(r => mutProvisioningContext.interpretResult(verifier, r)))

      out <- F.ifThenElse(mutFailures.nonEmpty)(
        F.maybeSuspend(doFail(mutProvisioningContext.toImmutable)),
        runSteps(otherSteps).flatMap {
          _ =>
            F.ifThenElse(mutFailures.nonEmpty)(
              F.maybeSuspend(doFail(mutProvisioningContext.toImmutable)),
              F.maybeSuspend {
                val finalLocator = new LocatorDefaultImpl(diplan, Option(parentContext), makeMeta(), mutProvisioningContext.toImmutable)
                mutProvisioningContext.locatorRef.ref.set(Right(finalLocator))
                Right(finalLocator): Either[FailedProvision[F], LocatorDefaultImpl[F]]
              },
            )
        },
      )

    } yield {
      out
    }
  }

  private[this] def verifyEffectType[F[_]: TagK](
    ops: Seq[NonImportOp]
  )(implicit F: QuasiIO[F]
  ): F[Either[Seq[IncompatibleEffectTypesException], Unit]] = {
    val monadicOps = ops.collect { case m: MonadicOp => m }
    val badOps = monadicOps
      .filter(_.isIncompatibleEffectType[F])
      .map {
        op =>
          IncompatibleEffectTypesException(op, op.provisionerEffectType[F], op.actionEffectType)
      }

    F.ifThenElse(badOps.isEmpty)(F.pure(Right(())), F.pure(Left(badOps)))
  }

}
