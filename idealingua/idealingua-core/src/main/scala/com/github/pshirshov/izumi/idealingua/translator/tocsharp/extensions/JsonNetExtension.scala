package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSTContext
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.CogenProduct.{EnumProduct, IdentifierProduct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._

object JsonNetExtension extends CSharpTranslatorExtension {

  override def handleEnum(ctx: CSTContext, enum: Enumeration, product: EnumProduct): EnumProduct = {
    product
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
  }

  override def handleIdentifier(ctx: CSTContext, id: Identifier, product: IdentifierProduct): IdentifierProduct = {
    product
  }
}

/*
object EnumHelpersExtension extends TypeScriptTranslatorExtension {
  override def handleEnum(ctx: TSTContext, enum: TypeDef.Enumeration, product: EnumProduct): EnumProduct = {
    val it = enum.members.iterator
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

 */

/*

using Newtonsoft.Json;
using System;

namespace idltest.enums {

    public class TestEnumConverter : JsonConverter
    {
        public override void WriteJson(JsonWriter writer, object value, JsonSerializer serializer)
        {
            TestEnum v = (TestEnum)value;
            writer.WriteValue(v.ToString());
        }

        public override object ReadJson(JsonReader reader, Type objectType, object existingValue, JsonSerializer serializer)
        {
            return TestEnumHelpers.From((string)reader.Value);
        }

        public override bool CanConvert(Type objectType)
        {
            return objectType == typeof(string);
        }
    }
}

 */
