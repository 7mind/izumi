package izumi.idealingua.translator.toscala.extensions

import izumi.idealingua.model.common.TypeId.{DTOId, InterfaceId}
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.TypeDef.Interface
import izumi.idealingua.translator.toscala.STContext
import izumi.idealingua.translator.toscala.products.CogenProduct.{CompositeProduct, InterfaceProduct}
import izumi.idealingua.translator.toscala.types.{ScalaStruct, StructContext}
import izumi.idealingua.translator.toscala.tools.ScalaMetaTools._

import scala.meta._

object CastUpExtension extends ScalaTranslatorExtension {


  override def handleComposite(ctx: STContext, struct: StructContext, product: CompositeProduct): CompositeProduct = {
    val ext = generateUpcasts(ctx, struct.struct)
    product.copy(companionBase = product.companionBase.appendDefinitions(ext))

  }

  override def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    import ctx.conv._
    val struct = ctx.typespace.structure.structure(interface).toScala
    val ext = generateUpcasts(ctx,  struct)
    product.copy(companionBase = product.companionBase.appendDefinitions(ext))
  }

  private def generateUpcasts(ctx: STContext, interface: ScalaStruct): List[Stat] = {
    ctx.typespace.structure
      .structuralParents(interface.fields)
      .map {
        struct =>
          val parentImplId = struct.id match {
            case i: InterfaceId =>
              ctx.typespace.tools.implId(i)
            case d: DTOId =>
              d
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

          val name = Term.Name(s"${thisType.termName.value}_upcast_${parentType.termName.value}")

          q"""
             implicit object $name extends ${ctx.rt.Cast.parameterize(List(thisType.typeFull, parentType.typeFull)).init()} {
               override def convert(_value: ${thisType.typeFull}): ${parentType.typeFull} = {
                 assert(_value != null)
                 ${parentImplType.termFull}(..$constructorCode)
               }
             }
           """
      }

  }
}
