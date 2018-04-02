package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Interface
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.InterfaceProduct

import scala.meta._

object IfaceNarrowersExtension extends ScalaTranslatorExtension {

  override def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    val narrowers = ctx.typespace.structure
      .structuralParents(interface)
      .map {
        struct =>
          val dtoId = struct.id match {
            case i: InterfaceId =>
              ctx.typespace.implId(i)
            case o =>
              throw new IDLException(s"Not an interface: $o")
          }

          val constructorCode = struct.all.map {
            f =>
              q""" ${Term.Name(f.field.name)} = _value.${Term.Name(f.field.name)}  """
          }

          val tt = ctx.conv.toScala(dtoId)
          val parentType = ctx.conv.toScala(struct.id)
          q"""def ${Term.Name("as" + struct.id.name.capitalize)}(): ${parentType.typeFull} = {
             ${tt.termFull}(..$constructorCode)
            }
          """
      }

    import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._

    product.copy(tools = product.tools.extendDefinition(narrowers))
  }

}
