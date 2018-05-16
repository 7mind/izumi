package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Interface
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.InterfaceProduct

import scala.meta._


object IfaceConstructorsExtension extends ScalaTranslatorExtension {

  import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaField._


  override def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    val constructors = ctx.typespace.structure.conversions(interface.id).map {
      t =>

        val constructorCode = t.allFields.map {
          f =>
            f.sourceFieldName match {
              case Some(sourceFieldName) =>
                q""" ${Term.Name(f.targetFieldName)} = ${Term.Name(f.source.sourceName)}.${Term.Name(sourceFieldName)}  """

              case None =>
                q""" ${Term.Name(f.targetFieldName)} = ${Term.Name(f.source.sourceName)}  """

            }
        }

        val constructorSignature = t.outerParams
          .map(f => (Term.Name(f.sourceName), ctx.conv.toScala(f.sourceType).typeFull))
          .toParams

        val impl = t.typeToConstruct

        q"""def ${Term.Name("to" + impl.name)}(..$constructorSignature): ${ctx.conv.toScala(impl).typeFull} = {
            ${ctx.conv.toScala(impl).termFull}(..$constructorCode)
            }
          """
    }

    import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._

    product.copy(tools = product.tools.appendDefinitions(constructors))
  }
}
