package izumi.flow.dsl

import izumi.flow.dsl.FlowOp.MultiOp

import scala.collection.mutable

class MultiProcessor(op: MultiOp, registry: Registry, buffer: StreamBuffer) extends Processor {
  override def process(): PollingState = {
    op match {
      case FlowOp.FZip(inputs, output) =>
        ???
    }
  }

  private val goodChunks = new mutable.HashMap[ValueId, StreamState.ChunkReady]()

  //  def pollInputs(): PollingState = this.synchronized {
  //
  ////    println(s"Node ${op.output} is going to poll its inputs: ${op.inputs}")
  //
  //    val chunks = op.inputs.filterNot(i => goodChunks.contains(i)).map(i => (i, registry.node(i).poll(op.output)))
  //
  //    goodChunks.addAll(chunks.collect({ case (id, c: StreamState.ChunkReady) => (id, c) }))
  //
  //    println(s"Node ${op.output} has inputs state: $chunks")
  //    if (goodChunks.keySet == op.inputs.toSet) { // have chunks for all the inputs, ready to process
  //      val inputChunks = op.inputs.map(i => goodChunks(i).chunk)
  //      val outChunk = interpret(op, inputChunks)
  //      nextChunkReady(outChunk)
  //      goodChunks.clear()
  //      if (outChunk.exists(_.isInstanceOf[StreamState.Final])) {
  //        done = true
  //        PollingState(true)
  //      } else {
  //        PollingState(false)
  //      }
  //    } else if (chunks.exists(_._2 == StreamState.ChunkNotReady)) {
  //      PollingState(false)
  //    } else if (chunks.forall(_._2.isInstanceOf[StreamState.Final])) { // all inputs finished
  //      done = true
  //      PollingState(true)
  //    } else {
  //      throw new IllegalStateException(s"Node ${op.output} got inconsistent inputs: $chunks")
  //    }
  //  }
}
