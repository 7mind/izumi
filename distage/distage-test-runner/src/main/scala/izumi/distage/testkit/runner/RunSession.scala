package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure as Failed, Success}
import scala.util.control.NonFatal

private[runner] final case class ResolvedProvider(provider: ExecutionProvider, tests: Vector[TestDescriptor])
private[runner] final case class PlannedProvider(plan: ExecutionPlan, tests: Vector[TestDescriptor])

final class ResolvedRun private[runner] (
  val request: RunRequest,
  val tests: Vector[TestDescriptor],
  private[runner] val providers: Vector[ResolvedProvider],
  private[runner] val owner: RunSession,
) {
  val description: ResolvedSelection = ResolvedSelection(request, tests)
}

final class PlannedRun private[runner] (
  val description: PlannedSelection,
  private[runner] val plans: Vector[PlannedProvider],
  private[runner] val owner: RunSession,
) {
  val tests: Vector[TestDescriptor] = description.selection.tests
}

final class RunSession(
  identity: CatalogueIdentity,
  factories: Vector[() => TestSuite],
  executionContext: ExecutionContext,
  eventSink: EventSink,
) {
  private val registrationContext = new RegistrationContext(identity.target, executionContext)
  private val cancellation = new Cancellation
  private var discovery = Option.empty[Either[Failure, Catalogue]]
  private var registered = Vector.empty[RegisteredSuite]
  private var planningStarted = false
  private var executionStarted = false
  private var activeExecution = Option.empty[Promise[RunOutcome]]
  private var ownedPlans = Vector.empty[Future[OwnedPlan]]
  private var closing = Option.empty[Promise[Unit]]

  private final class OwnedPlan(val plan: ExecutionPlan) {
    lazy val close: Future[Unit] = try plan.close() catch { case NonFatal(cause) => Future.failed(cause) }
  }

  def close(): Future[Unit] = {
    implicit val ec: ExecutionContext = executionContext
    val (completion, admitted) = synchronized {
      closing match {
        case Some(previous) => (previous.future, None)
        case None =>
          val requested = Promise[Unit]()
          closing = Some(requested)
          (requested.future, Some((requested, ownedPlans, activeExecution.map(_.future))))
      }
    }
    admitted.foreach { case (requested, owners, execution) =>
      if (synchronized(executionStarted)) cancel()
      val settled = owners.map(_.transformWith {
        case Success(owner) => owner.close.transform(result => Success(result))
        case Failed(_) => Future.successful(Success(()))
      })
      val executions = execution.toVector.map(_.transform(_ => Success(Success(()))))
      val _ = requested.completeWith(Future.sequence(settled ++ executions).flatMap { results =>
        results.collect { case Failed(cause) => cause }.toList match {
          case Nil => Future.unit
          case cause :: Nil => Future.failed(cause)
          case primary :: others => Future.failed(new SessionFinalizationException(primary, others))
        }
      })
    }
    completion
  }

  def cancel(): Unit = cancellation.request()

  def discover(): Either[Failure, Catalogue] = synchronized {
    discovery match {
      case Some(result) => result
      case None =>
        val result = try {
          val suites = factories.map(factory => factory().register(registrationContext))
          registrationContext.freeze()
          val tests = suites.flatMap(_.tests)
          val duplicateSuites = suites.groupBy(_.descriptor.id).collect { case (id, values) if values.size > 1 => id }
          val duplicateTests = tests.groupBy(_.id).collect { case (id, values) if values.size > 1 => id }
          if (identity.build.value.isEmpty || identity.target.value.isEmpty || identity.catalogue.value.isEmpty) Left(RunnerFailure.message(FailurePhase.Discovery, "Catalogue identity must not be empty"))
          else if (suites.exists(_.descriptor.id.value.isEmpty)) Left(RunnerFailure.message(FailurePhase.Discovery, "Suite identity must not be empty"))
          else if (duplicateSuites.nonEmpty) Left(RunnerFailure.message(FailurePhase.Discovery, s"Duplicate suite identities: ${duplicateSuites.mkString(", ")}"))
          else if (duplicateTests.nonEmpty) Left(RunnerFailure.message(FailurePhase.Discovery, s"Duplicate test identities: ${duplicateTests.mkString(", ")}"))
          else if (suites.exists(suite => suite.tests.exists(test => test.id.target != identity.target || suite.descriptor.id != test.id.suite || test.id.path.isEmpty))) {
            Left(RunnerFailure.message(FailurePhase.Discovery, "Registered test identity does not belong to its catalogue"))
          } else {
            val catalogue = Catalogue(identity, suites.map(_.descriptor), tests)
            ProtocolCodec.validate(ProtocolMessage.Discovered(RunId("discovery"), catalogue)).left.map(error => RunnerFailure.message(FailurePhase.Discovery, error.message)).map { _ =>
              registered = suites
              catalogue
            }
          }
        } catch { case NonFatal(cause) => Left(RunnerFailure.fromThrowable(FailurePhase.Discovery, cause)) }
        discovery = Some(result)
        result
    }
  }

  def resolve(request: RunRequest): Either[Failure, ResolvedRun] = discover().flatMap { catalogue =>
    if (request.identity != catalogue.identity) Left(RunnerFailure.message(FailurePhase.Selection, "Saved request refers to a stale catalogue"))
    else if (request.overrides.axes.map(_.axis).distinct.size != request.overrides.axes.size || request.overrides.axisFilters.map(_.axis).distinct.size != request.overrides.axisFilters.size) {
      Left(RunnerFailure.message(FailurePhase.Selection, "An axis must have exactly one requested value"))
    } else {
      val selected = request.selection match {
        case Selection.All => Right(catalogue.tests)
        case Selection.Only(suites, tests) =>
          val unknownSuites = suites.filterNot(id => catalogue.suites.exists(_.id == id))
          val unknownTests = tests.filterNot(id => catalogue.tests.exists(_.id == id))
          if (suites.isEmpty && tests.isEmpty) Left(RunnerFailure.message(FailurePhase.Selection, "Explicit selection must not be empty"))
          else if (unknownSuites.nonEmpty || unknownTests.nonEmpty) Left(RunnerFailure.message(FailurePhase.Selection, s"Unknown explicit identities: suites=$unknownSuites tests=$unknownTests"))
          else Right(catalogue.tests.filter(test => suites.contains(test.id.suite) || tests.contains(test.id)))
      }
      selected.flatMap { tests =>
        val providers = registered.map(_.provider).foldLeft(Vector.empty[ExecutionProvider]) { (values, provider) =>
          if (values.exists(_ eq provider)) values else values :+ provider
        }
        providers.foldLeft[Either[Failure, Vector[ResolvedProvider]]](Right(Vector.empty)) { (previous, provider) =>
          previous.flatMap { groups =>
            val providerIds = registered.filter(_.provider eq provider).flatMap(_.tests).map(_.id)
            val providerTests = tests.filter(test => providerIds.contains(test.id))
            if (providerTests.isEmpty) Right(groups)
            else {
              val resolution = try provider.resolve(providerTests, request.overrides) catch { case NonFatal(cause) => Left(RunnerFailure.fromThrowable(FailurePhase.Selection, cause)) }
              resolution.flatMap { resolved =>
                if (resolved.exists(test => !providerTests.exists(_.id == test.id)) || resolved.map(_.id).distinct.size != resolved.size) {
                  Left(RunnerFailure.message(FailurePhase.Selection, "Execution provider returned invalid resolved identities"))
                } else Right(groups :+ ResolvedProvider(provider, resolved))
              }
            }
          }
        }.flatMap { groups =>
          val resolved = groups.flatMap(_.tests)
          if (resolved.isEmpty) Left(RunnerFailure.message(FailurePhase.Selection, "Selection matched no tests"))
          else {
            val result = new ResolvedRun(request, resolved, groups, this)
            ProtocolCodec.validate(ProtocolMessage.Resolved(RunId("resolution"), result.description))
              .left.map(error => RunnerFailure.message(FailurePhase.Selection, error.message)).map(_ => result)
          }
        }
      }
    }
  }

  def plan(resolved: ResolvedRun): Future[Either[Failure, PlannedRun]] = {
    implicit val ec: ExecutionContext = executionContext
    var admissions = Vector.empty[Promise[OwnedPlan]]
    val rejection = synchronized {
      if (resolved.owner ne this) Some(RunnerFailure.message(FailurePhase.Planning, "Resolved selection belongs to another session"))
      else if (closing.nonEmpty) Some(RunnerFailure.message(FailurePhase.Planning, "Session is closing"))
      else if (planningStarted) Some(RunnerFailure.message(FailurePhase.Planning, "Session planning has already started"))
      else {
        planningStarted = true
        admissions = resolved.providers.map(_ => Promise[OwnedPlan]())
        ownedPlans = admissions.map(_.future)
        None
      }
    }
    rejection match {
      case Some(failure) => Future.successful(Left(failure))
      case None =>
        val plans = resolved.providers.zip(admissions).map { case (group, admission) =>
          val acquisition = try group.provider.plan(group.tests) catch { case NonFatal(cause) => Future.failed(cause) }
          val _ = admission.completeWith(acquisition.map(new OwnedPlan(_)))
          admission.future.map { owner =>
            val value = owner.plan
            if (value.tests.map(_.id).toSet != group.tests.map(_.id).toSet || value.tests.map(_.id).distinct.size != value.tests.size) {
              Left(RunnerFailure.message(FailurePhase.Planning, "Execution plan changed selected test identities"))
            } else value.inspection.validate(group.tests.map(_.id))
              .left.map(error => RunnerFailure.message(FailurePhase.Planning, error)).map(_ => PlannedProvider(value, group.tests))
          }.recover { case NonFatal(cause) => Left(RunnerFailure.fromThrowable(FailurePhase.Planning, cause)) }
        }
        val assembled = Future.sequence(plans).map { results =>
          results.collect { case Left(failure) => failure }.toList match {
            case primary :: additional => Left(RunnerFailure.appendSuppressed(primary, additional.toVector))
            case Nil =>
              val providers = results.collect { case Right(value) => value }
              var rootOffset = 0
              var keyOffset = 0
              val inspections = providers.map { provider =>
                val local = provider.plan.inspection
                val roots = local.scopes.filter(_.id.path.size == 1).map(_.id.path.head).zipWithIndex.map { case (root, index) => root -> (index + rootOffset) }.toMap
                val keys = local.keys.zipWithIndex.map { case (key, index) => key.id -> DependencyKeyId(index + keyOffset) }.toMap
                rootOffset += roots.size
                keyOffset += keys.size
                val scopes = local.scopes.map { scope =>
                  scope.copy(
                    id = PlanScopeId(scope.id.path.updated(0, roots(scope.id.path.head))),
                    steps = scope.steps.map(step => step.copy(key = keys(step.key), dependencies = step.dependencies.map(keys))),
                  )
                }
                PlanInspection(local.keys.map(key => key.copy(id = keys(key.id))), scopes, local.failures)
              }
              val inspection = PlanInspection(inspections.flatMap(_.keys), inspections.flatMap(_.scopes), inspections.flatMap(_.failures))
              val description = PlannedSelection(resolved.description, inspection)
              ProtocolCodec.validate(ProtocolMessage.Planned(RunId("planning"), description))
                .left.map(error => RunnerFailure.message(FailurePhase.Planning, error.message)).map(_ => new PlannedRun(description, providers, this))
          }
        }.recover { case NonFatal(cause) => Left(RunnerFailure.fromThrowable(FailurePhase.Planning, cause)) }
        def rejectedPlan(failure: Failure): Future[Either[Failure, PlannedRun]] = close().transform {
          case Success(_) => Success(Left(failure))
          case Failed(cause) => Success(Left(RunnerFailure.appendSuppressed(failure, Vector(RunnerFailure.fromThrowable(FailurePhase.Finalization, cause)))))
        }
        assembled.flatMap {
          case Left(failure) => rejectedPlan(failure)
          case Right(value) =>
            if (synchronized(closing.nonEmpty)) rejectedPlan(RunnerFailure.message(FailurePhase.Planning, "Session closed during planning"))
            else Future.successful(Right(value))
        }
    }
  }

  def execute(run: RunId, request: RunRequest): Future[RunOutcome] = {
    implicit val ec: ExecutionContext = executionContext
    if (run.value.isEmpty) Future.failed(new IllegalArgumentException("Run identity must not be empty"))
    else resolve(request) match {
      case Left(failure) => Future.successful(rejected(run, failure))
      case Right(resolved) => plan(resolved).flatMap {
        case Left(failure) => Future.successful(rejected(run, failure))
        case Right(planned) => execute(run, planned)
      }
    }
  }

  def execute(run: RunId, planned: PlannedRun): Future[RunOutcome] = {
    implicit val ec: ExecutionContext = executionContext
    if (run.value.isEmpty) return Future.failed(new IllegalArgumentException("Run identity must not be empty"))
    val admitted = Promise[RunOutcome]()
    val rejection = synchronized {
      if (planned.owner ne this) Some(RunnerFailure.message(FailurePhase.Setup, "Execution plan belongs to another session"))
      else if (closing.nonEmpty) Some(RunnerFailure.message(FailurePhase.Setup, "Session is closing"))
      else if (executionStarted) Some(RunnerFailure.message(FailurePhase.Setup, "Session execution has already started"))
      else { executionStarted = true; activeExecution = Some(admitted); None }
    }
    rejection match {
      case Some(failure) => Future.successful(rejected(run, failure))
      case None =>
        val emitter = new RunEventEmitter(run, eventSink)
        val execution = Future { emitter.emit(RunEvent.Started(run)) }.flatMap { _ =>
          val executions = planned.plans.map { provider =>
            val reports = new ProviderEventEmitter(run, provider.tests, emitter)
            val context = RunExecutionContext(run, cancellation, reports.emit)
            val execution = try provider.plan.execute(context) catch { case NonFatal(cause) => Future.failed(cause) }
            execution.recover { case NonFatal(cause) => ProviderOutcome(Vector.empty, Vector(RunnerFailure.fromThrowable(FailurePhase.Setup, cause)), cancelled = false) }.map(reports.reconcile)
          }
          Future.sequence(executions).flatMap { completed =>
            val releases = ownedPlans.map(_.flatMap(_.close).transform(result => Success(result)))
            Future.sequence(releases).map { finalized =>
              val finalizationFailures = finalized.collect { case Failed(cause) => RunnerFailure.fromThrowable(FailurePhase.Finalization, cause) }
              val results = completed.flatMap(_.results)
              val expectedIds = planned.tests.map(_.id).toSet
              val completenessFailures =
                if (results.map(_.id).toSet != expectedIds || results.map(_.id).distinct.size != results.size) Vector(RunnerFailure.message(FailurePhase.Transport, "Terminal results do not match the complete selected test set"))
                else Vector.empty
              val outcome = RunOutcome(run, results, completed.flatMap(_.failures) ++ RunnerFailure.unreported(completed.flatMap(_.failures), finalizationFailures) ++ completenessFailures, cancellation.isRequested || completed.exists(_.cancelled))
              emitter.emitFailures(outcome.failures)
              emitter.emit(RunEvent.Finished(run, outcome))
              outcome
            }
          }
        }
        val _ = admitted.completeWith(execution)
        admitted.future
    }
  }

  private def rejected(run: RunId, failure: Failure): RunOutcome = {
    val outcome = RunOutcome(run, Vector.empty, Vector(failure), cancellation.isRequested)
    val emitter = new RunEventEmitter(run, eventSink)
    emitter.emit(RunEvent.PhaseFailed(run, failure))
    emitter.emit(RunEvent.Finished(run, outcome))
    outcome
  }
}

private[runner] final class SessionFinalizationException(primary: Throwable, additional: List[Throwable])
  extends RuntimeException("Multiple failures during session finalization", primary) {
  additional.foreach(addSuppressed)
}

private[runner] object RunnerCompletion {
  def after[A](completion: Future[A], release: () => Future[Unit])(implicit ec: ExecutionContext): Future[A] = completion.transformWith { result =>
    val closed = try release() catch { case NonFatal(cause) => Future.failed(cause) }
    closed.transform {
      case Success(_) => result
      case Failed(cause) => result match {
        case Success(_) => Failed(cause)
        case Failed(primary) => Failed(new SessionFinalizationException(primary, List(cause)))
      }
    }
  }
}

private[runner] final class RunEventEmitter(run: RunId, sink: EventSink) {
  private var sequence = 0L
  private var finished = false
  private var reportedFailures = Vector.empty[Failure]

  def emitFailures(failures: Vector[Failure]): Unit = synchronized {
    RunnerFailure.unreported(reportedFailures, failures).foreach(failure => emit(RunEvent.PhaseFailed(run, failure)))
  }

  def emit(event: RunEvent): Unit = synchronized {
    require(!finished, "Event emitted after terminal run completion")
    require(event.run == run, "Event belongs to a different run")
    val message = ProtocolMessage.Event(sequence, event)
    sequence += 1
    sink.accept(message)
    event match {
      case _: RunEvent.Finished => finished = true
      case RunEvent.PhaseFailed(_, failure) => reportedFailures :+= failure
      case _ => ()
    }
  }
}
