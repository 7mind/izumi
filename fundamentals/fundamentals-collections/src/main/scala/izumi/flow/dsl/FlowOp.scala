package izumi.flow.dsl

import izumi.flow.dsl.FlowOp.{FConstMulti, FFilter, FMap}
import izumi.flow.dsl.StreamSource.StreamState
import izumi.flow.dsl.Test.interpretNs
import izumi.flow.schema.{FType, FValue}
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import jdk.nashorn.api.scripting.ScriptObjectMirror

import java.util
import javax.script.{Bindings, SimpleBindings}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class ValueId(name: String, tpe: FType)

sealed trait FlowOp {
  val inputs: List[ValueId]
  val output: ValueId
}

object FlowOp {
  sealed trait PipeOp extends FlowOp {
    val input: ValueId
    val output: ValueId
    override val inputs: List[ValueId] = List(input)
  }

  case class FConstMulti(output: ValueId, value: List[FValue]) extends FlowOp {
    override val inputs: List[ValueId] = List.empty
  }

  case class FMap(input: ValueId, output: ValueId, expr: FExpr) extends PipeOp
  case class FFilter(input: ValueId, output: ValueId, expr: FExpr) extends PipeOp
}

sealed trait FExpr {
  //val closure: List[ValueId]
  val output: FType
}

object FExpr {
  case class NashornOp(expr: String, output: FType) extends FExpr
}

case class Flow(ops: List[FlowOp], outputs: List[ValueId]) {
  def dependencies: IncidenceMatrix[ValueId] = IncidenceMatrix(ops.map(op => (op.output, op.inputs.toSet)).toMap)
  def dependees: IncidenceMatrix[ValueId] = dependencies.transposed
  def index: Map[ValueId, FlowOp] = ops.map(op => (op.output, op)).toMap
}

trait StreamSource {
  def next(id: ValueId, requestor: ValueId): StreamState
}

object StreamSource {
  sealed trait StreamState
  object StreamState {
    sealed trait Final extends StreamState
    case class ChunkReady(chunk: List[FValue]) extends StreamState
    case object ChunkNotReady extends StreamState
    case object StreamFinished extends Final
    case class StreamErrored() extends Final
  }
}

object Test {
  val flow = Flow(
    List(
      FConstMulti(ValueId("ints", FType.FInt), List(FValue.FVInt(1), FValue.FVInt(2), FValue.FVInt(3))),
      FMap(ValueId("ints", FType.FInt), ValueId("ints_multiplied", FType.FInt), FExpr.NashornOp("self * 2", FType.FInt)),
    ),
    List(ValueId("ints_multiplied", FType.FInt)),
  )

  def main(args: Array[String]): Unit = {
    val streams = new mutable.HashMap[ValueId, mutable.ArrayBuffer[StreamState]]

    val state = new mutable.HashMap[(ValueId, ValueId), Int]
    val index = flow.index
    val src = new StreamSource {
      override def next(id: ValueId, requestor: ValueId): StreamState = synchronized {
        val next = state.getOrElseUpdate((id, requestor), 0)
        streams.get(id) match {
          case Some(value) =>
            state.put((id, requestor), next + 1)
            value(next)
          case None =>
            StreamState.ChunkNotReady
        }
      }
    }

    val threads = flow.ops.map {
      op =>
        new Thread(new Runnable {

          override def run(): Unit = {
            var stop = false
            val goodChunks = new mutable.HashMap[ValueId, StreamState.ChunkReady]()
            while (!stop) {
              val chunks = op.inputs.filterNot(i => goodChunks.contains(i)).map(i => (i, src.next(i, op.output)))
              goodChunks.addAll(chunks.collect({ case (id, c: StreamState.ChunkReady) => (id, c) }))
              println(s"Processing $op => $chunks")

              if (goodChunks.keySet == op.inputs.toSet) {
                val inputChunks = op.inputs.map(i => goodChunks(i).chunk)
                val outChunk = interpret(op, inputChunks)
                streams.getOrElseUpdate(op.output, ArrayBuffer.empty[StreamState]) ++= outChunk
                goodChunks.clear()
                if (outChunk.exists(_.isInstanceOf[StreamState.Final])) {
                  stop = true
                }
                println(s"=>$outChunk")
              } else if (chunks.exists(_._2.isInstanceOf[StreamState.Final])) {
                stop = true
              } else if (chunks.exists(_._2 == StreamState.ChunkNotReady)) {
                // next iteration
              } else {
                ???
              }
            }

          }
        })
    }

    threads.foreach(_.start())
    threads.foreach(_.join())

    println(streams)
  }
  def interpret(op: FlowOp, inputChunks: List[List[FValue]]): List[StreamState] = {
    op match {
      case FConstMulti(output, value) =>
        List(StreamState.ChunkReady(value), StreamState.StreamFinished)
      case FFilter(input, output, expr) =>
        ???
      case FMap(input, output, expr) =>
        assert(inputChunks.size == 1)
        val mapped = inputChunks.head.map {
          v =>
            val ov = interpretNs(v, output.tpe, expr)
            ov
        }
        List(StreamState.ChunkReady(mapped))
    }
  }

  import javax.script.ScriptEngine
  import javax.script.ScriptEngineManager
  val manager = new ScriptEngineManager
  val engine = manager.getEngineByName("nashorn")

  def interpretNs(iv: FValue, outType: FType, expr: FExpr): FValue = {
    expr match {
      case FExpr.NashornOp(expr, output) =>
        val b = new util.HashMap[String, AnyRef]()
        b.put("self", iv.valueRef)
        val bindings = new SimpleBindings(b)
        val out = engine.eval(expr, bindings)
        outType match {
          case FType.FInt =>
            FValue.FVInt(out.asInstanceOf[Double].intValue())
          case _ =>
            ???
        }
    }
  }
}
