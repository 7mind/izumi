package izumi.flow.dsl.simulator.processing

import izumi.flow.dsl.simulator.components.{NsInterpreter, Registry, StreamBuffer}
import izumi.flow.dsl.simulator.model.{PollingState, StreamState}
import izumi.flow.model.flow.FlowOp.{FFilter, FMap, PipeOp}
import izumi.flow.model.schema.FType
import izumi.flow.model.values.FValue

class PipeProcessor(op: PipeOp, registry: Registry, buffer: StreamBuffer, nsi: NsInterpreter) extends Processor {
  def process(): PollingState = {
    registry.node(op.input).poll(op.output) match {
      case value: StreamState.Final =>
        PollingState(true)
      case StreamState.ChunkReady(chunk) =>
        buffer.nextStates(interpretPipe(op, chunk))
        PollingState(false)
      case StreamState.ChunkNotReady =>
        PollingState(false)
    }
  }

  def interpretPipe(op: PipeOp, chunk: List[FValue]): List[StreamState] = {
    op match {
      case FMap(input, output, expr) =>
        val mapped = chunk.map(i => nsi.interpretNs(i, output.tpe, expr))
        List(StreamState.ChunkReady(mapped))
      case FFilter(input, output, expr) =>
        val mapped = chunk.filter(i => nsi.interpretNs(i, FType.FBool, expr).value.asInstanceOf[Boolean])
        if (mapped.nonEmpty) {
          List(StreamState.ChunkReady(mapped))
        } else {
          List(StreamState.ChunkNotReady)
        }
    }
  }
}
