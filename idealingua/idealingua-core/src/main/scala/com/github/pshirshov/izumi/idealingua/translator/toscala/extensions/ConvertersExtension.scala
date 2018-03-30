package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaStruct

import scala.meta._

object ConvertersExtension extends ScalaTranslatorExtension {

  import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._

  override def handleCompositeTools(ctx: STContext, obj: ScalaStruct, defn: Defn.Class): Defn.Class = {
    val converters = mkConverters(ctx, obj)
    defn.extendDefinition(converters)
  }


  override def handleInterfaceTools(ctx: STContext, iface: ILAst.Interface, defn: Defn.Class): Defn.Class = {
    import ctx.conv._
    val converters = mkConverters(ctx, ctx.typespace.structure(iface).toScala)
    defn.extendDefinition(converters)
  }

  private def mkConverters(ctx: STContext, struct: ScalaStruct): List[Defn.Def] = {
    ctx.typespace.sameSignature(struct.id).map {
      same =>
        val code = struct.all.map {
          f =>
            q""" ${f.name} = _value.${f.name}  """
        }
        q"""def ${Term.Name("cast" + same.id.name.capitalize)}(): ${ctx.conv.toScala(same.id).typeFull} = {
              ${ctx.conv.toScala(same.id).termFull}(..$code)
            }
          """
    }
  }
}
