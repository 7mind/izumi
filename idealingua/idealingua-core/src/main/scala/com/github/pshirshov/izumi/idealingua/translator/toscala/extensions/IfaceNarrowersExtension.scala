package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst.Interface
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.CogenProduct.InterfaceProduct

import scala.meta._

object IfaceNarrowersExtension extends ScalaTranslatorExtension {

  override def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    // we don't add explicit parents here because their converters are available
    val allStructuralParents = List(interface.id) ++ interface.superclasses.concepts

    val narrowers = allStructuralParents.distinct.map {
      p =>
        val ifields = ctx.typespace.enumFields(p)

        val constructorCode = ifields.all.map {
          f =>
            q""" ${Term.Name(f.field.name)} = _value.${Term.Name(f.field.name)}  """
        }

        import ctx.conv._
        val parentType = ctx.conv.toScala(p)
        val tt = parentType.within(ctx.typespace.toDtoName(p))
        q"""def ${Term.Name("as" + p.name.capitalize)}(): ${parentType.typeFull} = {
             ${tt.termFull}(..$constructorCode)
            }
          """
    }

    import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._

    product.copy(tools = product.tools.extendDefinition(narrowers))
  }

}
