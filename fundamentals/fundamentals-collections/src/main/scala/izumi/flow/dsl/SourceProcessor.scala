package izumi.flow.dsl

import izumi.flow.dsl.FlowOp.{FConstMulti, SourceOp}

class SourceProcessor(op: SourceOp, buffer: StreamBuffer) extends Processor {
  def process(): PollingState = {
    op match {
      case FConstMulti(output, value) =>
        val onePerChunk = value.map(v => StreamState.ChunkReady(List(v)))
        buffer.nextStates(onePerChunk ++ List(StreamState.StreamFinished))
        PollingState(true)
    }
  }
}
