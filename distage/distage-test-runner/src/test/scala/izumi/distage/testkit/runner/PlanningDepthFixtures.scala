package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import scala.concurrent.{ExecutionContext, Future}

private[runner] object PlanningDepthFixtures {
  def run(context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    def cause(label: String): Throwable = (1 until ProtocolCodec.MaxFailureDepth).foldLeft[Throwable](new IllegalStateException(label + " leaf")) { (inner, depth) =>
      new IllegalStateException(label + " depth " + depth, inner)
    }
    Vector(false, true).foldLeft(Future.unit) { (before, closeFails) => before.flatMap { _ =>
      val first = cause("first")
      val second = cause("second")
      val identity = CatalogueIdentity(BuildId("planning-depth"), BuildTargetId("depth-target"), CatalogueId(closeFails.toString))
      def suite(name: String, original: Throwable, ownsPlan: Boolean): TestSuite = FixtureSupport.suite(name, Vector("body")) { _ =>
        new FixtureSupport.Provider {
          override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = {
            if (!ownsPlan) Future.failed(original)
            else Future.successful(new FixtureSupport.Plan(selected) {
              override def execute(execution: RunExecutionContext): Future[ProviderOutcome] = Future.failed(new IllegalStateException("Rejected planning must not execute"))
              override def close(): Future[Unit] = Future.failed(original)
            })
          }
        }
      }
      val firstCaptured = RunnerFailure.fromThrowable(FailurePhase.Planning, first)
      val secondCaptured = RunnerFailure.fromThrowable(FailurePhase.Finalization, second)
      val run = RunId("depth-" + closeFails)
      verify(ProtocolCodec.validate(ProtocolMessage.Rejected(run, firstCaptured)).isRight && ProtocolCodec.validate(ProtocolMessage.Rejected(run, secondCaptured)).isRight, "Independent failures at the graph-depth bound are representable")
      val session = new RunSession(identity, Vector(() => suite("FirstFailure", first, false), () => suite("SecondFailure", second, closeFails)), context, FixtureSupport.silentSink())
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val selected = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
      session.plan(selected).map { result =>
        val failure = result.swap.getOrElse(throw new IllegalStateException("Depth control returned a valid plan"))
        val message = ProtocolMessage.Rejected(run, failure)
        val valid = ProtocolCodec.validate(message)
        println("PLANNING_DEPTH_OBSERVED closeFailure=" + closeFails + " wireValid=" + valid.isRight + " validation=" + valid)
        verify(valid.isRight && ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "Composed planning failures remain wire-valid at the graph-depth boundary: close=" + closeFails)
        def overflow(current: Failure): Boolean =
          (current.phase == FailurePhase.Transport && current.message == "Exception failure graph depth exceeds the protocol limit") || current.causes.exists(overflow) || current.suppressed.exists(overflow)
        verify(overflow(failure) && failure.message == first.getMessage && failure.suppressed.head.message == second.getMessage && first.getSuppressed.isEmpty && second.getSuppressed.isEmpty,
          "Nested graph overflow is explicit and retains both independent roots without mutating original throwables")
      }
    } }.map { _ => println("PLANNING_DEPTH_CONTRACTS_OK cases=2") }
  }
}
