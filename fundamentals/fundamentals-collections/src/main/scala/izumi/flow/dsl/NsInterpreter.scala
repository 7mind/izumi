package izumi.flow.dsl

import izumi.flow.schema.{FType, FValue}

import java.util
import javax.script.SimpleBindings

class NsInterpreter() {
  import javax.script.ScriptEngineManager
  val manager = new ScriptEngineManager
  val engine = manager.getEngineByName("nashorn")

  def interpretNs(iv: FValue, outType: FType, expr: FExpr): FValue = {
    expr match {
      case FExpr.NashornOp(expr, input, output) =>
        assert(outType == output)
        assert(iv.tpe == input, s"${iv.tpe} != $input")

        val b = new util.HashMap[String, AnyRef]()
        b.put("self", iv.valueRef)
        val bindings = new SimpleBindings(b)
        val out = engine.eval(expr, bindings)
        outType match {
          case FType.FBool =>
            FValue.FVBool(out.asInstanceOf[Boolean])
          case FType.FInt =>
            FValue.FVInt(out.asInstanceOf[Double].intValue())
          case _ =>
            ???
        }
    }
  }
}
