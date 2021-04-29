package izumi.flow.dsl

import izumi.flow.dsl.FlowOp.{FConstMulti, FMap, FZip}
import izumi.flow.schema.{FType, FValue}
import izumi.fundamentals.graphs.struct.IncidenceMatrix

case class ValueId(name: String, tpe: FType)

sealed trait FlowOp {
  val inputs: List[ValueId]
  val output: ValueId
}

object FlowOp {
  sealed trait PipeOp extends FlowOp {
    val input: ValueId
    override val inputs: List[ValueId] = List(input)
  }

  sealed trait SourceOp extends FlowOp {
    override val inputs: List[ValueId] = List.empty
  }

  sealed trait MultiOp extends FlowOp

  case class FConstMulti(output: ValueId, value: List[FValue]) extends SourceOp

  case class FMap(input: ValueId, output: ValueId, expr: FExpr) extends PipeOp
  case class FFilter(input: ValueId, output: ValueId, expr: FExpr) extends PipeOp

  case class FZip(inputs: List[ValueId], output: ValueId) extends MultiOp
}

sealed trait FExpr {
  //val closure: List[ValueId]
  val output: FType
}

object FExpr {
  case class NashornOp(expr: String, input: FType, output: FType) extends FExpr
}

case class Flow(ops: List[FlowOp], outputs: List[ValueId]) {
  def dependencies: IncidenceMatrix[ValueId] = IncidenceMatrix(ops.map(op => (op.output, op.inputs.toSet)).toMap)
  def dependees: IncidenceMatrix[ValueId] = dependencies.transposed
  def index: Map[ValueId, FlowOp] = ops.map(op => (op.output, op)).toMap
}
