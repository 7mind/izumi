package com.github.pshirshov.izumi.idealingua.translator.tocsharp.types

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Enumeration
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpImports

final case class CSharpType (
                              id: TypeId,
                              im: CSharpImports = CSharpImports(List.empty),
                              ts: Typespace = null
                            ) {

  def renderType(serialized: Boolean = false): String = {
    renderNativeType(id, serialized)
  }

  private def renderNativeType(id: TypeId, serialized: Boolean): String = id match {
    case g: Generic => renderGenericType(g, serialized)
    case p: Primitive => renderPrimitiveType(p, serialized)
    case _ => renderUserType(id, serialized)
  }

  private def renderGenericType(generic: Generic, serialized: Boolean): String = {
    generic match {
      case gm: Generic.TMap => s"Dictionary<${renderNativeType(gm.keyType, serialized)}, ${renderNativeType(gm.valueType, serialized)}>"
      case gl: Generic.TList => s"List<${renderNativeType(gl.valueType, serialized)}>"
      case gs: Generic.TSet => s"List<${renderNativeType(gs.valueType, serialized)}>"
      case go: Generic.TOption => s"Nullable<${renderNativeType(go.valueType, serialized)}>"
    }
  }

  protected def renderPrimitiveType(primitive: Primitive, serialized: Boolean = false): String = primitive match {
    case Primitive.TBool => "bool"
    case Primitive.TString => "string"
    case Primitive.TInt8 => "sbyte"
    case Primitive.TInt16 => "short"
    case Primitive.TInt32 => "int"
    case Primitive.TInt64 => "long"
    case Primitive.TFloat => "float"
    case Primitive.TDouble => "double"
    case Primitive.TUUID => "GUID"
    case Primitive.TTime => if (serialized) "string" else "TimeSpan"
    case Primitive.TDate => if (serialized) "string" else "Date"
    case Primitive.TTs => if (serialized) "string" else "DateTime"
    case Primitive.TTsTz => if (serialized) "string" else "DateTime"
  }

  protected def renderUserType(id: TypeId, serialized: Boolean = false, forAlias: Boolean = false, forMap: Boolean = false): String = {
//    if (serialized) {
//      id match {
//        case _: InterfaceId => s"map[string]json.RawMessage"
//        case _: AdtId => s"json.RawMessage" // TODO Consider exposing ADT as map[string]json.RawMessage so we can see the internals of it
//        case _: IdentifierId | _: EnumId => s"string"
//        case d: DTOId => s"${if (forAlias) "" else "*"}${im.withImport(d)}${d.name}Serialized"
//        case al: AliasId => if (isPrimitive(ts.dealias(al))) id.name else renderNativeType(ts.dealias(al), serialized)
//        case _ => throw new IDLException(s"Impossible renderUserType ${id.name}")
//      }
//    } else {
      id match {
        case _: EnumId => s"${im.withImport(id)}${id.name}"
        case _: InterfaceId => s"${im.withImport(id)}${id.name}"
        case _: IdentifierId => s"${im.withImport(id)}${id.name}"
        case _: AdtId | _: DTOId => s"${im.withImport(id)}${id.name}"
        case al: AliasId => s"${im.withImport(id)}${id.name}"
        case _ => throw new IDLException(s"Impossible renderUserType ${id.name}")
      }
//    }
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
             id: TypeId,
             im: CSharpImports = CSharpImports(List.empty),
             ts: Typespace = null
           ): CSharpType = new CSharpType(id, im, ts)
}
