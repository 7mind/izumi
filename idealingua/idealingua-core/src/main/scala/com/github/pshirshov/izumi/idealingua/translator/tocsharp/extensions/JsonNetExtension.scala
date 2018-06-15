package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.discard
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSTContext
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.CogenProduct.{EnumProduct, IdentifierProduct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._

object JsonNetExtension extends CSharpTranslatorExtension {
  override def preModelEmit(ctx: CSTContext, id: Identifier): String = {
    discard(ctx)
    s"[JsonConverter(typeof(${id.id.name}_JsonNetConverter))]"
  }

  override def postModelEmit(ctx: CSTContext, id: Identifier): String = {
    discard(ctx)
    s"""public class ${id.id.name}_JsonNetConverter: JsonConverter
       |{
       |    public override void WriteJson(JsonWriter writer, object value, JsonSerializer serializer)
       |    {
       |        ${id.id.name} id = (${id.id.name})value;
       |        writer.WriteValue(id.ToString());
       |    }
       |
       |    public override object ReadJson(JsonReader reader, Type objectType, object existingValue, JsonSerializer serializer)
       |    {
       |        return ${id.id.name}.From((string)reader.Value);
       |    }
       |
       |    public override bool CanConvert(Type objectType)
       |    {
       |        return objectType == typeof(${id.id.name});
       |    }
       |}
     """.stripMargin
  }

  override def imports(ctx: CSTContext, id: Identifier): List[String] = {
    discard(ctx)
    List("Newtonsoft.Json")
  }

  override def preModelEmit(ctx: CSTContext, id: Enumeration): String = {
    discard(ctx)
    s"[JsonConverter(typeof(${id.id.name}_JsonNetConverter))]"
  }

  override def postModelEmit(ctx: CSTContext, id: Enumeration): String = {
    discard(ctx)
    s"""public class ${id.id.name}_JsonNetConverter: JsonConverter
       |{
       |    public override void WriteJson(JsonWriter writer, object value, JsonSerializer serializer)
       |    {
       |        ${id.id.name} v = (${id.id.name})value;
       |        writer.WriteValue(v.ToString());
       |    }
       |
       |    public override object ReadJson(JsonReader reader, Type objectType, object existingValue, JsonSerializer serializer)
       |    {
       |        return ${id.id.name}Helpers.From((string)reader.Value);
       |    }
       |
       |    public override bool CanConvert(Type objectType)
       |    {
       |        return objectType == typeof(${id.id.name});
       |    }
       |}
     """.stripMargin
  }


  override def imports(ctx: CSTContext, id: Enumeration): List[String] = {
    discard(ctx)
    List("Newtonsoft.Json")
  }


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
