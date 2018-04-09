package com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TSTContext
import com.github.pshirshov.izumi.idealingua.translator.totypescript.products.CogenProduct.EnumProduct
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

object EnumHelpersExtension extends TypeScriptTranslatorExtension {
  override def handleEnum(ctx: TSTContext, enum: TypeDef.Enumeration, product: EnumProduct): EnumProduct = {
    val it = enum.members.iterator
    val values = it.map { m =>
      s"${enum.id.name}.${m}" + (if (it.hasNext) "," else "")
    }.mkString("\n")

    val extension =
      s"""
         |export class ${enum.id.name}Helpers {
         |    public static readonly all = [
         |${values.shift(8)}
         |    ]
         |
         |    public static isValid(value: string): boolean {
         |        return ${enum.id.name}Helpers.all.indexOf(value) >= 0;
         |    }
         |}
       """.stripMargin

    EnumProduct(product.content + extension, product.preamble)
  }
}
