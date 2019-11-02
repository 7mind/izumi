package izumi.idealingua.translator.toscala.extensions

import izumi.idealingua.model.il.ast.typed.TypeDef
import izumi.idealingua.translator.toscala.STContext
import izumi.idealingua.translator.toscala.products.CogenProduct.{CompositeProduct, InterfaceProduct}
import izumi.idealingua.translator.toscala.types.{ScalaStruct, StructContext}

import scala.meta._

object CastSimilarExtension extends ScalaTranslatorExtension {

  import izumi.idealingua.translator.toscala.tools.ScalaMetaTools._


  override def handleComposite(ctx: STContext, interface: StructContext, product: CompositeProduct): CompositeProduct = {
    val converters = mkConverters(ctx, interface.struct)
    product.copy(companionBase = product.companionBase.appendDefinitions(converters))

  }

  override def handleInterface(ctx: STContext, interface: TypeDef.Interface, product: InterfaceProduct): InterfaceProduct = {
    import ctx.conv._
    val converters = mkConverters(ctx, ctx.typespace.structure.structure(interface).toScala)
    product.copy(companionBase = product.companionBase.appendDefinitions(converters))
  }


  private def mkConverters(ctx: STContext, struct: ScalaStruct): List[Stat] = {
    ctx.typespace.structure.sameSignature(struct.id).map {
      same =>
        val code = struct.all.map {
          f =>
            q""" ${f.name} = _value.${f.name}  """
        }

        val thisType = ctx.conv.toScala(struct.id)
        val targetType = ctx.conv.toScala(same.id)

        val name = Term.Name(s"${thisType.termName.value}_cast_into_${same.id.uniqueDomainName}")

        q"""
             implicit object $name extends ${ctx.rt.Cast.parameterize(List(thisType.typeFull, targetType.typeFull)).init()} {
               override def convert(_value: ${thisType.typeFull}): ${targetType.typeFull} = {
                  assert(_value != null)
                  ${targetType.termFull}(..$code)
               }
             }
           """
    }
  }
}
