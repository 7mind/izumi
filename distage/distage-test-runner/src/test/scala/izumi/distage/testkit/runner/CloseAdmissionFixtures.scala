package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import scala.concurrent.{ExecutionContext, Future, Promise}

private[runner] object CloseAdmissionFixtures {
  def run(verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = new ExecutionContext {
      override def execute(task: Runnable): Unit = task.run()
      override def reportFailure(cause: Throwable): Unit = throw cause
    }
    val identity = CatalogueIdentity(BuildId("close-admission"), BuildTargetId("close-target"), CatalogueId("close-catalogue"))
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    def exercise(application: Boolean): Future[Unit] = {
      val publication = Promise[ExecutionPlan]()
      val entered = Promise[Unit]()
      val release = Promise[Unit]()
      var cancellation = Option.empty[Cancellation]
      var pending = Option.empty[ExecutionPlan]
      val suite = new TestSuite {
        override def register(registration: RegistrationContext): RegisteredSuite = {
          val descriptor = SuiteDescriptor(SuiteId("CloseAdmission"), "CloseAdmission")
          val test = TestDescriptor(TestId(registration.target, descriptor.id, Vector("body"), None), "body", SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
          val provider = new ExecutionProvider {
            override def resolve(selected: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = Right(selected)
            override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = {
              val plan = new ExecutionPlan {
                override val tests: Vector[TestDescriptor] = selected
                override val inspection: PlanInspection = PlanInspection.individualTests(selected.map(_.id))
                override def execute(execution: RunExecutionContext): Future[ProviderOutcome] = {
                  cancellation = Some(execution.cancellation)
                  val registration = execution.cancellation.onRequest(() => release.future)
                  val _ = entered.success(())
                  release.future.flatMap(_ => registration.close()).map { _ =>
                    val cancelled = execution.cancellation.isRequested
                    val result = TestResult(test.id, if (cancelled) TestStatus.Cancelled else TestStatus.Succeeded, None, 0L)
                    execution.emit(ProviderEvent.TestCompleted(result))
                    ProviderOutcome(Vector(result), Vector.empty, cancelled)
                  }
                }
              }
              if (application) { pending = Some(plan); publication.future } else Future.successful(plan)
            }
          }
          RegisteredSuite(descriptor, Vector(test), provider)
        }
      }
      val run = RunId(if (application) "queued-application" else "active-session")
      val output = new ProtocolOutput { override def accept(message: ProtocolMessage): Unit = () }
      val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = () }
      val (completed, closed) = if (application) {
        val app = new TestApplication(run, identity, Vector(() => suite), ec, output)
        val executing = app.accept(ProtocolMessage.Request(RequestOperation.Execute, run, request))
        val closing = app.close()
        val _ = publication.success(pending.getOrElse(throw new IllegalStateException("Planning did not begin")))
        (executing, closing)
      } else {
        val session = new RunSession(identity, Vector(() => suite), ec, sink)
        val executing = session.execute(run, request).map(_ => ())
        (executing, session.close())
      }
      entered.future.flatMap { _ =>
        val requested = cancellation.exists(_.isRequested)
        val joined = !closed.isCompleted && !completed.isCompleted
        val _ = release.success(())
        completed.flatMap(_ => closed).map { _ =>
          verify(requested, run.value + " close requests cancellation for every admitted execution")
          verify(joined, run.value + " close waits for the admitted execution and held cancellation finalizer")
        }
      }
    }
    exercise(application = true).flatMap(_ => exercise(application = false)).map { _ => println("CLOSE_ADMISSION_CONTRACTS_OK cases=2") }
  }
}
