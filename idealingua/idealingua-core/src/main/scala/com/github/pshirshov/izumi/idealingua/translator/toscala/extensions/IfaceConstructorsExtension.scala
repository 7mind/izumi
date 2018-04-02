package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Interface
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.CogenProduct.InterfaceProduct

import scala.meta._

object IfaceConstructorsExtension extends ScalaTranslatorExtension {

  import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaField._


  override def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    val constructors = ctx.typespace.compatibleImplementors(interface.id).map {
      t =>
        val instanceFields = t.parentInstanceFields
        val childMixinFields = t.mixinsInstancesFields
        val localFields = t.localFields

        val constructorSignature = Seq(
          childMixinFields.map(_.definedBy).map(f => (ctx.tools.idToParaName(f), ctx.conv.toScala(f).typeFull))
          , localFields.map(f => (Term.Name(f.name), ctx.conv.toScala(f.typeId).typeFull))
        ).flatten.toParams

        val constructorCodeThis = instanceFields.toList.map {
          f =>
            q""" ${Term.Name(f.name)} = _value.${Term.Name(f.name)}  """
        }

        val constructorCodeOthers = childMixinFields.map {
          f =>
            q""" ${Term.Name(f.field.name)} = ${ctx.tools.idToParaName(f.definedBy)}.${Term.Name(f.field.name)}  """
        }

        val constructorCodeNonUnique = localFields.map {
          f =>
            val term = Term.Name(f.name)
            q""" $term = $term """
        }

        val impl = t.typeToConstruct

        q"""def ${Term.Name("to" + impl.name.capitalize)}(..$constructorSignature): ${ctx.conv.toScala(impl).typeFull} = {
            ${ctx.conv.toScala(impl).termFull}(..${constructorCodeThis ++ constructorCodeOthers ++ constructorCodeNonUnique})
            }
          """
    }

    import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._

    product.copy(tools = product.tools.extendDefinition(constructors))
  }
}
