package izumi.idealingua.translator.toscala.extensions

import izumi.idealingua.model.il.ast.typed.TypeDef.Interface
import izumi.idealingua.translator.toscala.STContext
import izumi.idealingua.translator.toscala.products.CogenProduct.InterfaceProduct

import scala.meta._


object CastDownExpandExtension extends ScalaTranslatorExtension {

  override def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    val constructors = ctx.typespace.structure.conversions(interface.id).map {
      t =>

        val constructorCode = ctx.tools.makeConstructor(t)
        val constructorSignature = ctx.tools.makeParams(t)

        val impl = t.typeToConstruct

        val thisType = ctx.conv.toScala(interface.id)
        val targetType = ctx.conv.toScala(impl)
        val targetImplType = ctx.conv.toScala(impl)

        val name = Term.Name(s"${thisType.termName.value}_downcast_extend_${impl.uniqueDomainName}")

        q"""
             implicit object $name extends ${ctx.rt.Extend.parameterize(List(thisType.typeFull, targetType.typeFull)).init()} {
               class Call(private val _value: ${thisType.typeFull}) extends AnyVal {
                  def using(..${constructorSignature.params}): ${targetType.typeFull} = {
                    assert(_value != null)
                    ..${constructorSignature.assertion}
                    ${targetImplType.termFull}(..$constructorCode)
                  }
               }

               override type INSTANTIATOR = Call

               override def next(_value: ${thisType.typeFull}): Call = new Call(_value)
             }
           """
    }

    import izumi.idealingua.translator.toscala.tools.ScalaMetaTools._

    product.copy(companionBase = product.companionBase.appendDefinitions(constructors))
  }


}
