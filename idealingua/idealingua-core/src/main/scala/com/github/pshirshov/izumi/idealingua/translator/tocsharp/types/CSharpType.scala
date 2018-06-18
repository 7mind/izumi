package com.github.pshirshov.izumi.idealingua.translator.tocsharp.types

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Enumeration
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpImports

final case class CSharpType (
                              id: TypeId)(implicit im: CSharpImports, ts: Typespace) {

  def isNative: Boolean = isNativeImpl(id)
  def isNullable: Boolean = isNullableImpl(id)
  def defaultValue: String = getDefaultValue(id)
  def getRandomValue: String = getRandomValue(id)
  def getInitValue: Option[String] = getInitValue(id)

  def isNullableImpl(id: TypeId): Boolean = id match {
    case g: Generic => g match {
      case _: Generic.TMap => true
      case _: Generic.TList => true
      case _: Generic.TSet => true
      case _: Generic.TOption => true
    }
    case p: Primitive => p match {
      case Primitive.TBool => false
      case Primitive.TString => true
      case Primitive.TInt8 => false
      case Primitive.TInt16 => false
      case Primitive.TInt32 => false
      case Primitive.TInt64 => false
      case Primitive.TFloat => false
      case Primitive.TDouble => false
      case Primitive.TUUID => true
      case Primitive.TTime => true
      case Primitive.TDate => true
      case Primitive.TTs => true
      case Primitive.TTsTz => true
    }
    case _ => id match {
      case _: EnumId => false
      case _: InterfaceId => true
      case _: IdentifierId => true
      case _: AdtId | _: DTOId => true
      case al: AliasId => isNullableImpl(ts.dealias(al))
      case _ => throw new IDLException(s"Impossible isNullableImpl type: ${id.name}")
    }
  }

  private def isNativeImpl(id: TypeId): Boolean = id match {
    case g: Generic => g match {
      case _: Generic.TMap => true
      case _: Generic.TList => true
      case _: Generic.TSet => true
      case _: Generic.TOption => true
    }
    case p: Primitive => p match {
      case Primitive.TBool => true
      case Primitive.TString => true
      case Primitive.TInt8 => true
      case Primitive.TInt16 => true
      case Primitive.TInt32 => true
      case Primitive.TInt64 => true
      case Primitive.TFloat => true
      case Primitive.TDouble => true
      case Primitive.TUUID => true
      case Primitive.TTime => true
      case Primitive.TDate => true
      case Primitive.TTs => true
      case Primitive.TTsTz => true
    }
    case _ => id match {
      case _: EnumId => true
      case _: InterfaceId => true
      case _: IdentifierId => true
      case _: AdtId | _: DTOId => true
      case al: AliasId => isNativeImpl(ts.dealias(al))
      case _ => throw new IDLException(s"Impossible isNativeImpl type: ${id.name}")
    }
  }

  private def getDefaultValue(id: TypeId): String = id match {
    case g: Generic => g match {
      case _: Generic.TMap => "null"
      case _: Generic.TList => "null"
      case _: Generic.TSet => "null"
      case _: Generic.TOption => "null"
    }
    case p: Primitive => p match {
      case Primitive.TBool => "false"
      case Primitive.TString => "null"
      case Primitive.TInt8 => "0"
      case Primitive.TInt16 => "0"
      case Primitive.TInt32 => "0"
      case Primitive.TInt64 => "0"
      case Primitive.TFloat => "0.0f"
      case Primitive.TDouble => "0.0"
      case Primitive.TUUID => "null"
      case Primitive.TTime => "null"
      case Primitive.TDate => "0"
      case Primitive.TTs => "0"
      case Primitive.TTsTz => "0"
    }
    case _ => id match {
      case e: EnumId => s"${e.name}.${ts(e).asInstanceOf[Enumeration].members.head}"
      case _: InterfaceId => "null"
      case _: IdentifierId => "null"
      case _: AdtId | _: DTOId => "null"
      case al: AliasId => getDefaultValue(ts.dealias(al))
      case _ => throw new IDLException(s"Impossible getDefaultValue type: ${id.name}")
    }
  }

  private def getInitValue(id: TypeId): Option[String] = id match {
    case g: Generic => g match {
      case _: Generic.TMap => Some(s"new ${renderType()}()")
      case _: Generic.TList => Some(s"new ${renderType()}()")
      case _: Generic.TSet => Some(s"new ${renderType()}()")
      case _: Generic.TOption => None
    }
    case p: Primitive => p match {
      case Primitive.TBool => None
      case Primitive.TString => None
      case Primitive.TInt8 => None
      case Primitive.TInt16 => None
      case Primitive.TInt32 => None
      case Primitive.TInt64 => None
      case Primitive.TFloat => None
      case Primitive.TDouble => None
      case Primitive.TUUID => None
      case Primitive.TTime => None
      case Primitive.TDate => None
      case Primitive.TTs => None
      case Primitive.TTsTz => None
    }
    case _ => id match {
      case e: EnumId => None
      case _: InterfaceId => None
      case _: IdentifierId => None
      case _: AdtId | _: DTOId => None
      case al: AliasId => getInitValue(ts.dealias(al))
      case _ => throw new IDLException(s"Impossible getInitValue type: ${id.name}")
    }
  }

  private def getRandomValue(id: TypeId): String = {
    val rnd = new scala.util.Random()
    id match {
      case g: Generic => g match {
        case _: Generic.TMap => "null"
        case _: Generic.TList => "null"
        case _: Generic.TSet => "null"
        case _: Generic.TOption => "null"
      }
      case p: Primitive => p match {
        case Primitive.TBool => rnd.nextBoolean().toString
        case Primitive.TString => "\"str_" + rnd.nextInt(20000) + "\""
        case Primitive.TInt8 => rnd.nextInt(127).toString
        case Primitive.TInt16 => (256 + rnd.nextInt(32767 - 255)).toString
        case Primitive.TInt32 => (32768 + rnd.nextInt(2147483647 - 32767)).toString
        case Primitive.TInt64 => (2147483648L + rnd.nextInt(2147483647)).toString
        case Primitive.TFloat => rnd.nextFloat().toString
        case Primitive.TDouble => (2147483648L + rnd.nextFloat()).toString
        case Primitive.TUUID => s"""new System.Guid("${java.util.UUID.randomUUID.toString}")"""
        case Primitive.TTime => s"${rnd.nextInt(24)}:${rnd.nextInt(60)}:${rnd.nextInt(60)}"
        case Primitive.TDate => s"${1984 + rnd.nextInt(20)}:${rnd.nextInt(12)}:${rnd.nextInt(28)}"
        case Primitive.TTs => s"${1984 + rnd.nextInt(20)}:${rnd.nextInt(12)}:${rnd.nextInt(28)}T${rnd.nextInt(24)}:${rnd.nextInt(60)}:${rnd.nextInt(60)}.${10000 + rnd.nextInt(10000)}+10:00"
        case Primitive.TTsTz => s"${1984 + rnd.nextInt(20)}:${rnd.nextInt(12)}:${rnd.nextInt(28)}T${rnd.nextInt(24)}:${rnd.nextInt(60)}:${rnd.nextInt(60)}.${10000 + rnd.nextInt(10000)}Z"
      }
      case _ => id match {
        case e: EnumId => {
          val enu = ts(e).asInstanceOf[Enumeration]
          s"${e.name}.${enu.members(rnd.nextInt(enu.members.length))}"
        }
        case _: InterfaceId => "null"
        case _: IdentifierId => "null"
        case _: AdtId | _: DTOId => "null"
        case al: AliasId => getRandomValue(ts.dealias(al))
        case _ => throw new IDLException(s"Impossible getRandomValue type: ${id.name}")
      }
    }
  }

    def renderToString(name: String, escape: Boolean): String = {
      val res = id match {
        case Primitive.TString => name
        case Primitive.TInt8 => return s"$name.ToString()"  // No Escaping needed for integers
        case Primitive.TInt16 => return s"$name.ToString()"
        case Primitive.TInt32 => return s"$name.ToString()"
        case Primitive.TInt64 => return s"$name.ToString()"
        case Primitive.TUUID => s"$name.ToString()"
        case _: EnumId => s"$name.ToString()"
        case _: IdentifierId => s"$name.ToString()"
        case _ => throw new IDLException(s"Should never render non int, string, or Guid types to strings. Used for type ${id.name}")
      }
      if (escape) {
        s"Uri.EscapeDataString($res)"
      } else {
        res
      }
    }

    def renderFromString(src: String, unescape: Boolean): String = {
      val source = if (unescape) s"Uri.UnescapeDataString($src)" else src
      id match {
          case Primitive.TString => source
          case Primitive.TInt8 => s"sbyte.Parse($src)"   // No Escaping needed for integers
          case Primitive.TInt16 => s"short.Parse($src)"
          case Primitive.TInt32 => s"int.Parse($src)"
          case Primitive.TInt64 => s"long.Parse($src)"
          case Primitive.TUUID => s"new Guid($source)"
          case e: EnumId => s"${e.name}Helpers.From(${src})"
          case i: IdentifierId => s"${i.name}.From(${src})"
          case _ => throw new IDLException(s"Should never render non int, string, or Guid types to strings. Used for type ${id.name}")
      }
    }

  def renderType(): String = {
    renderNativeType(id)
  }

  private def renderNativeType(id: TypeId): String = id match {
    case g: Generic => renderGenericType(g)
    case p: Primitive => renderPrimitiveType(p)
    case _ => renderUserType(id)
  }

  private def renderGenericType(generic: Generic): String = {
    generic match {
      case gm: Generic.TMap => s"Dictionary<${renderNativeType(gm.keyType)}, ${renderNativeType(gm.valueType)}>"
      case gl: Generic.TList => s"List<${renderNativeType(gl.valueType)}>"
      case gs: Generic.TSet => s"List<${renderNativeType(gs.valueType)}>"
      case go: Generic.TOption => if (!isNullableImpl(go.valueType)) s"Nullable<${renderNativeType(go.valueType)}>" else renderNativeType(go.valueType)
    }
  }

  protected def renderPrimitiveType(primitive: Primitive): String = primitive match {
    case Primitive.TBool => "bool"
    case Primitive.TString => "string"
    case Primitive.TInt8 => "sbyte"
    case Primitive.TInt16 => "short"
    case Primitive.TInt32 => "int"
    case Primitive.TInt64 => "long"
    case Primitive.TFloat => "float"
    case Primitive.TDouble => "double"
    case Primitive.TUUID => "Guid"
    case Primitive.TTime => "TimeSpan"
    case Primitive.TDate => "DateTime" // Could be Date
    case Primitive.TTs => "DateTime"
    case Primitive.TTsTz => "DateTime"
  }

  protected def renderUserType(id: TypeId, forAlias: Boolean = false, forMap: Boolean = false): String = {

      id match {
        case _: EnumId => s"${im.withImport(id)}"
        case _: InterfaceId => s"${im.withImport(id)}"
        case _: IdentifierId => s"${im.withImport(id)}"
        case _: AdtId | _: DTOId => s"${im.withImport(id)}"
        case al: AliasId => renderNativeType(ts.dealias(al))
        case _ => throw new IDLException(s"Impossible renderUserType ${id.name}")
      }
  }
//
//  private def renderUnmarshalShared(in: String, out: String, errorCheck: Boolean): String = {
//    if (!errorCheck) {
//      return s"json.Unmarshal($in, $out)"
//    }
//
//    s"""if err := json.Unmarshal($in, $out); err != nil {
//       |    return err
//       |}
//     """.stripMargin
//  }
//
//  def renderUnmarshal(content: String, assignLeft: String, assignRight: String = ""): String = {
//    val tempContent = s"m${content.capitalize}"
//
//    id match {
//      case _: InterfaceId =>
//        s"""var rawMap${content.capitalize} map[string]json.RawMessage
//           |if err := json.Unmarshal($content, &rawMap${content.capitalize}); err != nil {
//           |    return err
//           |}
//           |$tempContent, err := Create${id.name}(rawMap${content.capitalize})
//           |if err != nil {
//           |    return err
//           |}
//           |$assignLeft$tempContent$assignRight
//         """.stripMargin
//
//      case _: AdtId | _: DTOId | _: IdentifierId | _: AliasId =>
//        s"""$tempContent := &${renderType(forAlias = true)}{}
//           |${renderUnmarshalShared(content, tempContent, errorCheck = true)}
//           |$assignLeft$tempContent$assignRight
//         """.stripMargin
//
//      case g: Generic => g match {
//        case _: Generic.TMap => s"Not implemented renderUnmarshal.Generic.TMap"
//        case _: Generic.TList => s"Not implemented renderUnmarshal.Generic.TMap"
//        case _: Generic.TOption => s"Not implemented renderUnmarshal.Generic.TMap"
//        case _: Generic.TSet => s"Not implemented renderUnmarshal.Generic.TMap"
//      }
//
//      case _ => throw new IDLException("Primitive types should not be unmarshalled manually")
//      // case _ => assignLeft + content + assignRight
//    }
//  }
//
//  def renderToString(name: String): String = id match {
//    case Primitive.TString => name
//    case Primitive.TInt8 => s"strconv.FormatInt(int64($name), 10)"
//    case Primitive.TInt16 => s"strconv.FormatInt(int64($name), 10)"
//    case Primitive.TInt32 => s"strconv.FormatInt(int64($name), 10)"
//    case Primitive.TInt64 => s"strconv.FormatInt($name, 10)"
//    case Primitive.TUUID => name
//    case _ => throw new IDLException(s"Should never render non int or string types to strings. Used for type ${id.name}")
//  }
//
//  def renderFromString(dest: String, src: String, unescape: Boolean): String = {
//    if (unescape) {
//      id match {
//        case Primitive.TString => s"$dest, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}"
//        case Primitive.TInt8 => s"${dest}Str, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}\n${dest}64, err := strconv.ParseInt(${dest}Str, 10, 8)\nif err != nil {\n    return err\n}\n$dest := int8(${dest}64)"
//        case Primitive.TInt16 => s"${dest}Str, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}\n${dest}64, err := strconv.ParseInt(${dest}Str, 10, 16)\nif err != nil {\n    return err\n}\n$dest := int16(${dest}64)"
//        case Primitive.TInt32 => s"${dest}Str, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}\n${dest}64, err := strconv.ParseInt(${dest}Str, 10, 32)\nif err != nil {\n    return err\n}\n$dest := int32(${dest}64)"
//        case Primitive.TInt64 => s"${dest}Str, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}\n$dest, err := strconv.ParseInt(${dest}Str, 10, 64)\nif err != nil {\n    return err\n}"
//        case Primitive.TUUID => s"$dest, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}"
//        case _ => throw new IDLException(s"Should never parse non int or string types. Used for type ${id.name}")
//      }
//    } else {
//      throw new IDLException(s"Render from string for non unescaped ones is not supported yet!")
//    }
//  }
}

object CSharpType {
  def apply(
             id: TypeId
           )(implicit im: CSharpImports , ts: Typespace): CSharpType = new CSharpType(id)
}
