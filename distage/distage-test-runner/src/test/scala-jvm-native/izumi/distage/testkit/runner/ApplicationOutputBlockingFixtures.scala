package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.AnyWordSpec

object ApplicationOutputBlockingFixtures {
  def main(args: Array[String]): Unit = BlockingApplicationFixtures.run("APPLICATION_OUTPUT_BLOCKING", "output") { (gate, recorded) =>
    val output = new ProtocolOutput {
      override def accept(message: ProtocolMessage): Unit = synchronized {
        message match {
          case ProtocolMessage.Event(_, _: RunEvent.Started) => gate.hold()
          case _ => ()
        }
        recorded.accept(message)
      }
    }
    (() => new AnyWordSpec { "body after output" in () }, output)
  }
}
