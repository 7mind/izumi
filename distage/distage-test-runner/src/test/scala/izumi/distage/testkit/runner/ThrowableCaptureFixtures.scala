package izumi.distage.testkit.runner

import io.circe.Json
import io.circe.parser.parse
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.AnyWordSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

private[runner] object ThrowableCaptureFixtures {
  private final case class Sample(name: String, create: () => Throwable, message: String, causes: Vector[String], suppressed: Vector[String], failedField: Option[String])

  private final class RecordingSink extends EventSink {
    private var recorded = Vector.empty[ProtocolMessage.Event]
    override def accept(event: ProtocolMessage.Event): Unit = synchronized(recorded :+= event)
    def events: Vector[ProtocolMessage.Event] = synchronized(recorded)
  }

  def run(context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val accessors = Vector("message", "cause", "stack").map { field =>
      val create = () => field match {
        case "message" => new RuntimeException("original", new IllegalArgumentException("ordinary cause")) {
          override def getMessage: String = throw new IllegalStateException("message accessor")
        }
        case "cause" => new RuntimeException("original", new IllegalArgumentException("ordinary cause")) {
          override def getCause: Throwable = throw new IllegalStateException("cause accessor")
        }
        case "stack" => new RuntimeException("original", new IllegalArgumentException("ordinary cause")) {
          override def getStackTrace: Array[StackTraceElement] = throw new IllegalStateException("stack accessor")
        }
        case other => throw new IllegalArgumentException("Unknown accessor sample: " + other)
      }
      Sample(field, create, if (field == "message") "" else "original", if (field == "cause") Vector.empty else Vector("ordinary cause"), Vector.empty, Some(field))
    }
    val ordinary = Sample("ordinary", () => new RuntimeException("original", new IllegalArgumentException("ordinary cause")), "original", Vector("ordinary cause"), Vector.empty, None)
    val suppressed = Sample("suppressed", () => {
      val original = new RuntimeException("original", new IllegalArgumentException("ordinary cause"))
      original.addSuppressed(new IllegalStateException("suppressed one", new UnsupportedOperationException("suppressed cause")))
      original.addSuppressed(new IllegalArgumentException("suppressed two"))
      original
    }, "original", Vector("ordinary cause"), Vector("suppressed one", "suppressed two"), None)
    val samples = accessors ++ Vector(ordinary, suppressed)
    samples.foldLeft(Future.successful(())) { (before, sample) => before.flatMap { _ =>
      val original = sample.create()
      val stackFails = Try(sample.create().getStackTrace).isFailure
      val failedFields = (sample.failedField.toVector ++ (if (stackFails) Vector("stack") else Vector.empty)).distinct
      val captured = Try(RunnerFailure.fromThrowable(FailurePhase.Test, original))
      verify(captured.isSuccess, sample.name + " throwable conversion must preserve the original failure; accessorFailure=" + captured.failed.toOption.map(_.getClass.getName))
      check(captured.get, original, sample, failedFields, verify)
      final class Suite extends AnyWordSpec { "failure" in { throw original } }
      val identity = CatalogueIdentity(BuildId("throwable-capture"), BuildTargetId("capture-target"), CatalogueId(sample.name))
      val sink = new RecordingSink
      val session = new RunSession(identity, Vector(() => new Suite), context, sink)
      session.execute(RunId(sample.name), RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))).map { outcome =>
        verify(!outcome.successful && outcome.results.size == 1 && outcome.results.head.status == TestStatus.Failed && outcome.results.head.failure.nonEmpty, sample.name + " public runner must retain its failing body result")
        check(outcome.results.head.failure.get, original, sample, failedFields, verify)
        verify(sink.events.nonEmpty && sink.events.last.event == RunEvent.Finished(outcome.run, outcome), sample.name + " public runner must deliver terminal completion")
        verify(sink.events.forall(event => ProtocolCodec.decode(ProtocolCodec.encode(event)) == Right(event)), sample.name + " failure events must round-trip")
      }
    } }.map { _ =>
      graphBoundaries(verify)
      println("RUNNER_THROWABLE_CAPTURE_OK cases=" + samples.size + " accessors=explicit suppressed=distinct sessions=terminal")
    }
  }

  private def check(failure: Failure, original: Throwable, sample: Sample, failedFields: Vector[String], verify: (Boolean, String) => Unit): Unit = {
    verify(failure.phase == FailurePhase.Test && failure.exceptionClass == original.getClass.getName, sample.name + " captured failure must retain its original phase and class")
    verify(failure.message == sample.message, sample.name + " capture must retain an available message")
    verify(failure.causes.map(_.message) == sample.causes, sample.name + " causal edges must retain their meaning")
    val message = ProtocolMessage.Rejected(RunId(sample.name), failure)
    val json = parse(ProtocolCodec.encode(message)).fold(error => throw new IllegalStateException(error.message), value => value)
    val cursor = json.hcursor.downField("message").downField("failure")
    val errors = failedFields.map { field => Json.obj("field" -> Json.fromString(field), "exceptionClass" -> Json.fromString(classOf[IllegalStateException].getName)) }
    val capturedErrors = cursor.get[Vector[Json]]("captureErrors")
    verify(capturedErrors == Right(errors), sample.name + " unavailable fields must expose their accessor failures; expected=" + errors + " observed=" + capturedErrors)
    verify(cursor.get[Vector[Json]]("suppressed").map(_.map(_.hcursor.get[String]("message"))) == Right(sample.suppressed.map(Right(_))), sample.name + " suppressed edges must retain their separate ordered relation")
    if (sample.name == "suppressed") {
      verify(cursor.downField("suppressed").downArray.get[Vector[Json]]("causes").map(_.map(_.hcursor.get[String]("message"))) == Right(Vector(Right("suppressed cause"))), "Nested causes of suppressed failures must be retained")
    }
    verify(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), sample.name + " complete captured failure must round-trip")
  }

  private def graphBoundaries(verify: (Boolean, String) => Unit): Unit = {
    val root = new RuntimeException("cycle root")
    val child = new RuntimeException("cycle child")
    val _ = root.initCause(child)
    child.addSuppressed(root)
    val cycle = RunnerFailure.fromThrowable(FailurePhase.Test, root)
    verify(cycle.message == "cycle root" && cycle.causes.head.message == "cycle child" && cycle.causes.head.suppressed.head.phase == FailurePhase.Transport && cycle.causes.head.suppressed.head.message.contains("cycle"), "Mixed cause/suppressed cycles must retain ancestors and explicitly report the cycle")
    val cycleMessage = ProtocolMessage.Rejected(RunId("mixed-cycle"), cycle)
    verify(ProtocolCodec.decode(ProtocolCodec.encode(cycleMessage)) == Right(cycleMessage), "Mixed cause/suppressed cycles must produce valid protocol records")
    Vector(ProtocolCodec.MaxFailureDepth, ProtocolCodec.MaxFailureDepth + 1).foreach { depth =>
      val leaf = new RuntimeException("boundary leaf")
      val original = (1 until depth).foldLeft[Throwable](leaf) { (nested, index) =>
        val parent = new RuntimeException("mixed parent-" + index)
        if (index % 2 == 0) { val _ = parent.initCause(nested) } else parent.addSuppressed(nested)
        parent
      }
      val captured = RunnerFailure.fromThrowable(FailurePhase.Test, original)
      def terminal(failure: Failure): Failure = (failure.causes ++ failure.suppressed).headOption.fold(failure)(terminal)
      val last = terminal(captured)
      verify(if (depth == ProtocolCodec.MaxFailureDepth) last.message == "boundary leaf" && last.phase == FailurePhase.Test else last.phase == FailurePhase.Transport && last.message.contains("depth exceeds"), "Mixed cause/suppressed paths must share one depth budget: " + depth)
      val message = ProtocolMessage.Rejected(RunId("mixed-depth-" + depth), captured)
      verify(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "Mixed cause/suppressed boundaries must round-trip: " + depth)
    }
    println("RUNNER_THROWABLE_GRAPH_BOUNDARIES_OK nested=true mixedCycle=true mixedDepth=32+33")
  }
}
