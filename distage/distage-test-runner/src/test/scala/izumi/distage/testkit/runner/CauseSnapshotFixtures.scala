package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import java.util.concurrent.atomic.AtomicInteger

private[runner] object CauseSnapshotFixtures {
  def run(verify: (Boolean, String) => Unit): Unit = {
    Vector(false, true).foreach { hasCause =>
      val reads = new AtomicInteger(0)
      val leaf = new RuntimeException("stateful cause boundary") {
        override def getCause: Throwable = {
          if (reads.incrementAndGet() == 1 && !hasCause) null
          else new IllegalArgumentException("unrepresented child")
        }
      }
      val original = (1 until ProtocolCodec.MaxFailureDepth).foldLeft[Throwable](leaf) {
        (cause, index) => new RuntimeException("parent-" + index, cause)
      }
      val captured = RunnerFailure.fromThrowable(FailurePhase.Test, original)
      verify(reads.get() == 1, "Exception cause access must be snapshotted once at the protocol depth boundary hasCause=" + hasCause)
      def terminal(failure: Failure): Failure = failure.causes.headOption.fold(failure)(terminal)
      val last = terminal(captured)
      verify(
        if (hasCause) last.phase == FailurePhase.Transport && last.message.contains("depth exceeds")
        else last.exceptionClass == leaf.getClass.getName && last.message == "stateful cause boundary",
        "Captured cause boundaries must retain an absent cause or explicitly report truncation hasCause=" + hasCause,
      )
      val message = ProtocolMessage.Completed(RunOutcome(RunId("cause-snapshot"), Vector.empty, Vector(captured), cancelled = false))
      verify(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "Captured cause boundaries must produce a valid protocol record hasCause=" + hasCause)
    }
    println("RUNNER_CAUSE_SNAPSHOT_BOUNDARIES_OK")
  }
}
