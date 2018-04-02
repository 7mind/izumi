package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.{CompositeProudct, InterfaceProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaStruct

import scala.meta._

object ConvertersExtension extends ScalaTranslatorExtension {

  import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._


  override def handleComposite(ctx: STContext, interface: ScalaStruct, product: CompositeProudct): CompositeProudct = {
    val converters = mkConverters(ctx, interface)
    product.copy(tools = product.tools.extendDefinition(converters))

  }

  override def handleInterface(ctx: STContext, interface: TypeDef.Interface, product: InterfaceProduct): InterfaceProduct = {
    import ctx.conv._
    val converters = mkConverters(ctx, ctx.typespace.structure(interface).toScala)
    product.copy(tools = product.tools.extendDefinition(converters))
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
