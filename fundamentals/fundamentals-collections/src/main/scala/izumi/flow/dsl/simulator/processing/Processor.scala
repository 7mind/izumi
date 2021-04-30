package izumi.flow.dsl.simulator.processing

import izumi.flow.dsl.simulator.model.PollingState

trait Processor {
  def process(): PollingState
}
