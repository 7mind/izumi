package izumi.flow.dsl

trait Processor {
  def process(): PollingState
}
