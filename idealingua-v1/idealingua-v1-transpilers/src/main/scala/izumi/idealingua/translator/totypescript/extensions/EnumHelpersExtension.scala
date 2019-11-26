package izumi.idealingua.translator.totypescript.extensions

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.il.ast.typed.TypeDef
import izumi.idealingua.translator.totypescript.TSTContext
import izumi.idealingua.translator.totypescript.products.CogenProduct.EnumProduct

object EnumHelpersExtension extends TypeScriptTranslatorExtension {
  override def handleEnum(ctx: TSTContext, enum: TypeDef.Enumeration, product: EnumProduct): EnumProduct = {
    val it = enum.members.map(_.value).iterator
    val values = it.map { m =>
      s"${enum.id.name}.$m" + (if (it.hasNext) "," else "")
    }.mkString("\n")

    val extension =
      s"""
         |export class ${enum.id.name}Helpers {
         |    public static readonly all = [
         |${values.shift(8)}
         |    ];
         |
         |    public static isValid(value: string): boolean {
         |        return ${enum.id.name}Helpers.all.indexOf(value as ${enum.id.name}) >= 0;
         |    }
         |}
       """.stripMargin

    EnumProduct(product.content + extension, product.preamble)
  }
}
