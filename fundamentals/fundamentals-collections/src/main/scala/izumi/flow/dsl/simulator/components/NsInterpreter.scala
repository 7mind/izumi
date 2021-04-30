package izumi.flow.dsl.simulator.components

import izumi.flow.model.expr.FExpr
import izumi.flow.model.schema.FType
import izumi.flow.model.values.FValue
import izumi.flow.model.values.FValue.FVField
import jdk.nashorn.api.scripting.ScriptObjectMirror

import scala.jdk.CollectionConverters._
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
        b.put("self", buildInput(iv))
        val bindings = new SimpleBindings(b)
        val out = engine.eval(expr, bindings)
        reconstructValue(outType, out)
    }
  }

  private def reconstructValue(outType: FType, out: AnyRef): FValue = {
    outType match {
      case FType.FBool =>
        FValue.FVBool(out.asInstanceOf[Boolean])
      case FType.FInt =>
        val v = (out: Any) match {
          case d: Double =>
            d.intValue()
          case i: Int =>
            i
          case b =>
            scala.sys.error(s"Not an integer: $b")
        }
        FValue.FVInt(v)
      case t: FType.FRecord =>
        val o = out.asInstanceOf[ScriptObjectMirror]
        assert(o.size() == t.fields.size)
        val m = o.entrySet().asScala.map(e => (e.getKey, e.getValue)).toMap
        val fields = t.fields.map {
          f =>
            FVField(f.name, reconstructValue(f.tpe, m(f.name)))
        }
        FValue.FVRecord(fields, t)
      case t: FType.FTuple =>
        val o = out.asInstanceOf[ScriptObjectMirror]
        assert(o.isArray)
        assert(o.size() == t.tuple.size)
        FValue.FVTuple(o.values().asScala.zip(t.tuple).map { case (v, t) => reconstructValue(t, v) }.toList, t)
      case u =>
        scala.sys.error(s"Can't process output of type $u, got $out")
    }
  }

  private def buildInput(iv: FValue): AnyRef = {
    iv match {
      case builtin: FValue.FVBuiltin =>
        builtin.valueRef
      case FValue.FVRecord(fields, tpe) =>
        fields.map(f => (f.name, buildInput(f.value))).toMap.asJava
      case FValue.FVTuple(tuple, tpe) =>
        tuple.map(v => buildInput(v)).asJava
    }
  }
}
