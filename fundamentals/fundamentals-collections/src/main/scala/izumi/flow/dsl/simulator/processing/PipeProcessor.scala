package izumi.flow.dsl.simulator.processing

import izumi.flow.dsl.simulator.components.{NsInterpreter, Registry, StreamBuffer}
import izumi.flow.dsl.simulator.model.{PollingState, StreamState}
import izumi.flow.model.flow.FlowOp.{FFilter, FMap, PipeOp}
import izumi.flow.model.schema.FType
import izumi.flow.model.values.FValue

class PipeProcessor(op: PipeOp, registry: Registry, buffer: StreamBuffer, nsi: NsInterpreter) extends Processor {
  def process(): PollingState = {
    registry.node(op.input).poll(op.output) match {
      case _: StreamState.Final =>
        PollingState(true)
      case StreamState.ChunkReady(chunk) =>
        buffer.nextStates(interpretPipe(op, chunk))
        PollingState(false)
      case StreamState.ChunkNotReady =>
        PollingState(false)
    }
  }

  def interpretPipe(op: PipeOp, chunk: List[FValue]): List[StreamState] = {
    val it = op.input.tpe.asInstanceOf[FType.FStream].tpe
    val outt = op.output.tpe.asInstanceOf[FType.FStream].tpe
    op match {
      case m: FMap =>
        val mapped = chunk.map {
          i =>
            assert(i.tpe == i.tpe)
            nsi.interpretNs(i, outt, m.expr)
        }
        List(StreamState.ChunkReady(mapped))
      case m: FFilter =>
        assert(it == outt)

        val mapped = chunk.filter {
          i =>
            assert(i.tpe == it)
            nsi.interpretNs(i, FType.FBool, m.expr) match {
              case FValue.FVBool(value) =>
                value
              case _ =>
                ???
            }
        }
        if (mapped.nonEmpty) {
          List(StreamState.ChunkReady(mapped))
        } else {
          List(StreamState.ChunkNotReady)
        }
    }
  }
}
