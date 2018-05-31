package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSTContext
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

object Unity3DExtension extends CSharpTranslatorExtension {
  //  override def handleEnum(ctx: TSTContext, enum: TypeDef.Enumeration, product: EnumProduct): EnumProduct = {
  //    val it = enum.members.iterator
  //    val values = it.map { m =>
  //      s"${enum.id.name}.${m}" + (if (it.hasNext) "," else "")
  //    }.mkString("\n")
  //
  //    val extension =
  //      s"""
  //         |export class ${enum.id.name}Helpers {
  //         |    public static readonly all = [
  //         |${values.shift(8)}
  //         |    ]
  //         |
  //         |    public static isValid(value: string): boolean {
  //         |        return ${enum.id.name}Helpers.all.indexOf(value as ${enum.id.name}) >= 0;
  //         |    }
  //         |}
  //       """.stripMargin
  //
  //    EnumProduct(product.content + extension, product.preamble)
//  }
}
