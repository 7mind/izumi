package izumi.flow.dsl

import izumi.flow.dsl.FlowOp.{PipeOp, SourceOp}

import scala.collection.mutable

class NodeSimulator(op: FlowOp, clients: Set[ValueId], registry: Registry, isOut: Boolean) extends StreamBuffer {
  private val buffers = new mutable.HashMap[ValueId, mutable.ArrayBuffer[StreamState]]
  private var done: Boolean = false

  private val processor = op match {
    case op: PipeOp =>
      new PipeProcessor(op, registry, this, new NsInterpreter)
    case op: SourceOp =>
      new SourceProcessor(op, this)
    case op: FlowOp.MultiOp =>
      new MultiProcessor(op, registry, this)
  }

  {
    clients.foreach(c => buffers.put(c, mutable.ArrayBuffer.empty[StreamState]))
  }

  def makeThread(): Thread = {
    new Thread(
      new Runnable {
        override def run(): Unit = {
          while (!done) {
            done = processor.process().finished
          }
          println(s"${op.output} finished! Client buffers=${buffers.view.mapValues(b => (b.size)).toMap}")
        }
      },
      op.output.toString,
    )
  }

  def poll(requestor: ValueId): StreamState = this.synchronized {
//    println(s"Node ${op.output} got a request from client $requestor")
    val coll = buffers(requestor)
    if (coll.nonEmpty) {
      coll.remove(0)
    } else {
      if (done) {
        StreamState.StreamFinished
      } else {
        StreamState.ChunkNotReady
      }
    }
  }

  def nextStates(outChunk: List[StreamState]): Unit = {
    buffers.foreach {
      case (_, buffer) =>
        buffer ++= outChunk
    }
    if (isOut) {
      println(s"Output stream ${op.output} got new chunks: $outChunk")
    }
  }

}
