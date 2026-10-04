package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.ProtocolMessage

trait ProtocolOutput {
  def accept(message: ProtocolMessage): Unit
}
