package izumi.distage.testkit.runner

import izumi.distage.testkit.runner.spec.AnyWordSpec

object ApplicationBlockingFixtures {
  def main(args: Array[String]): Unit = BlockingApplicationFixtures.run("APPLICATION_INLINE_BLOCKING", "body") { (gate, output) =>
    (() => new AnyWordSpec { "held synchronous body" in { gate.hold() } }, output)
  }
}
