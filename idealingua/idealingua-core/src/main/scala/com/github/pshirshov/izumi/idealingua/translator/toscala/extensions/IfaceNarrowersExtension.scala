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
          val parentImplId = struct.id match {
            case i: InterfaceId =>
              ctx.typespace.implId(i)
            case o =>
              throw new IDLException(s"Not an interface: $o")
          }

          val constructorCode = struct.all.map {
            f =>
              q""" ${Term.Name(f.field.name)} = _value.${Term.Name(f.field.name)}  """
          }

          val thisType = ctx.conv.toScala(interface.id)
          val parentType = ctx.conv.toScala(struct.id)
          val parentImplType = ctx.conv.toScala(parentImplId)

          val name = Term.Name(s"${parentType.termName.value}_to_${parentType.termName.value}")

          q"""
             implicit object $name extends ${ctx.rt.Cast.parameterize(List(thisType.typeFull, parentType.typeFull)).init()} {
               override def convert(_value: ${thisType.typeFull}): ${parentType.typeFull} = {
                 assert(_value != null)
                 ${parentImplType.termFull}(..$constructorCode)
               }
             }
           """
      }

    import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._

    product.copy(companionBase = product.companionBase.appendDefinitions(narrowers))
  }

}
