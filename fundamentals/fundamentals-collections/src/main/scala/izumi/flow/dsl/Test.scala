package izumi.flow.dsl

import izumi.flow.dsl.FlowOp.{FConstMulti, FFilter, FMap, FZip}
import izumi.flow.schema.{FType, FValue}

object Test {
  val flow1 = Flow(
    List(
      FConstMulti(ValueId("ints_1", FType.FInt), Range(0, 10).map(idx => FValue.FVInt(idx)).toList),
      FFilter(ValueId("ints_1", FType.FInt), ValueId("ints_filtered", FType.FInt), FExpr.NashornOp("self % 2 == 0", FType.FInt, FType.FBool)),
      FMap(ValueId("ints_filtered", FType.FInt), ValueId("ints_multiplied", FType.FInt), FExpr.NashornOp("self * 2", FType.FInt, FType.FInt)),
    ),
    List(
      ValueId("ints_multiplied", FType.FInt)
    ),
  )
//  val flow2 = Flow(
//    List(
//      FConstMulti(ValueId("ints_1", FType.FInt), List(FValue.FVInt(1), FValue.FVInt(2), FValue.FVInt(3))),
//      FConstMulti(ValueId("ints_2", FType.FInt), List(FValue.FVInt(6), FValue.FVInt(5), FValue.FVInt(4))),
//      FZip(List(ValueId("ints_1", FType.FInt), ValueId("ints_1", FType.FInt)), ValueId("ints_zipped", FType.FInt)),
//      FMap(ValueId("ints_1", FType.FInt), ValueId("ints_multiplied", FType.FInt), FExpr.NashornOp("self * 2", FType.FInt)),
//    ),
//    List(
//      ValueId("ints_multiplied", FType.FInt),
//      ValueId("ints_zipped", FType.FInt),
//    ),
//  )

  def main(args: Array[String]): Unit = {
    val simulation = new Simulation(flow1)
    simulation.run()
  }

}
