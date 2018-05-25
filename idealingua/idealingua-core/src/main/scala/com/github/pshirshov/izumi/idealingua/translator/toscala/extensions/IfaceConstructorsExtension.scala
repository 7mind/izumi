package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Interface
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.InterfaceProduct

import scala.meta._


object IfaceConstructorsExtension extends ScalaTranslatorExtension {


  override def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    val constructors = ctx.typespace.structure.conversions(interface.id).map {
      t =>

        val constructorCode = ctx.tools.makeConstructor(t)
        val constructorSignature = ctx.tools.makeParams(t)
        val impl = t.typeToConstruct

        q"""def ${Term.Name("to" + impl.uniqueDomainName)}(..${constructorSignature.params}): ${ctx.conv.toScala(impl).typeFull} = {
             ..${constructorSignature.assertion}
            ${ctx.conv.toScala(impl).termFull}(..$constructorCode)
            }
          """
    }

    import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._

    product.copy(tools = product.tools.appendDefinitions(constructors))
  }


}
