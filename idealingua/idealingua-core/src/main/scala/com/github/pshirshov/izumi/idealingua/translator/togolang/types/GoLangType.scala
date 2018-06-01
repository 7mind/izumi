package com.github.pshirshov.izumi.idealingua.translator.togolang.types

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

final case class GoLangType (
                        id: TypeId,
                        im: GoLangImports = GoLangImports(List.empty),
                        ts: Typespace = null
                      ) {

  def hasSetterError(): Boolean = {
    hasSetterErrorImpl(id)
  }

  private def hasSetterErrorImpl(id: TypeId): Boolean = {
    id match {
      case Primitive.TUUID => true
      case g: Generic => g match {
        case _: Generic.TOption => false
        case _ => true
      }
      case _: InterfaceId | _: AdtId | _: DTOId | _: IdentifierId => true
      case al: AliasId => hasSetterErrorImpl(ts(al).asInstanceOf[Alias].target)
      case _ => false
    }
  }

  def renderType(serialized: Boolean = false, forAlias: Boolean = false, forMap: Boolean = false): String = {
    renderNativeType(id, serialized, forAlias, forMap)
  }

  private def renderNativeType(id: TypeId, serialized: Boolean, forAlias: Boolean = false, forMap: Boolean = false): String = id match {
    case g: Generic => renderGenericType(g, serialized, forMap)
    case p: Primitive => renderPrimitiveType(p, serialized)
    case _ => renderUserType(id, serialized, forAlias, forMap)
  }

  private def renderGenericType(generic: Generic, serialized: Boolean, forMap: Boolean): String = generic match {
    case gm: Generic.TMap => s"map[${renderNativeType(gm.keyType, serialized, forMap = true)}]${renderNativeType(gm.valueType, serialized)}"
    case gl: Generic.TList => s"[]${renderNativeType(gl.valueType, serialized)}"
    case gs: Generic.TSet => s"[]${renderNativeType(gs.valueType, serialized)}"
    case go: Generic.TOption => s"*${renderNativeType(go.valueType, serialized)}"
  }

  def isPrimitive(id: TypeId): Boolean = id match {
    case _: Primitive => true
    case _: DTOId => false
    case _: IdentifierId => false
    case _: AdtId => false
    case _: InterfaceId => false
    case _: EnumId => false
    case al: AliasId => isPrimitive(ts(al).asInstanceOf[Alias].target)
    case g: Generic => g match {
      case go: Generic.TOption => isPrimitive(go.valueType)
      case gl: Generic.TList => isPrimitive(gl.valueType)
      case gs: Generic.TSet => isPrimitive(gs.valueType)
      case gm: Generic.TMap => isPrimitive(gm.valueType)
    }
    case _ => throw new IDLException("Unknown type is checked for primitiveness " + id.name)
  }

  def isPolymorph(id: TypeId): Boolean = id match {
    case _: Primitive => false
    case _: DTOId => true
    case _: IdentifierId => true
    case _: AdtId => true
    case _: InterfaceId => true
    case _: EnumId => true
    case al: AliasId => isPolymorph(ts(al).asInstanceOf[Alias].target)
    case g: Generic => g match {
      case go: Generic.TOption => isPolymorph(go.valueType)
      case gl: Generic.TList => isPolymorph(gl.valueType)
      case gs: Generic.TSet => isPolymorph(gs.valueType)
      case gm: Generic.TMap => isPolymorph(gm.valueType)
    }
    case _ => false
  }

  protected def renderPrimitiveType(primitive: Primitive, serialized: Boolean = false): String = primitive match {
    case Primitive.TBool => "bool"
    case Primitive.TString => "string"
    case Primitive.TInt8 => "int8"
    case Primitive.TInt16 => "int16"
    case Primitive.TInt32 => "int32"
    case Primitive.TInt64 => "int64"
    case Primitive.TFloat => "float32"
    case Primitive.TDouble => "float64"
    case Primitive.TUUID => "string"
    case Primitive.TTime => if (serialized) "string" else "time.Time"
    case Primitive.TDate => if (serialized) "string" else "time.Time"
    case Primitive.TTs => if (serialized) "string" else "time.Time"
    case Primitive.TTsTz => if (serialized) "string" else "time.Time"
  }

  protected def renderUserType(id: TypeId, serialized: Boolean = false, forAlias: Boolean = false, forMap: Boolean = false): String = {
    if (serialized) {
      id match {
        case _: InterfaceId => s"map[string]json.RawMessage"
        case _: AdtId => s"json.RawMessage" // TODO Consider exposing ADT as map[string]json.RawMessage so we can see the internals of it
        case _: IdentifierId | _: EnumId => s"string"
        case d: DTOId => s"${if (forAlias) "" else "*"}${im.withImport(d)}${d.name}Serialized"
        case al: AliasId => if (isPrimitive(ts.dealias(al))) id.name else renderNativeType(ts.dealias(al), serialized)
        case _ => throw new IDLException(s"Impossible renderUserType ${id.name}")
      }
    } else {
      id match {
        case _: EnumId => if(forMap) "string" else s"${im.withImport(id)}${id.name}"
        case _: InterfaceId => s"${im.withImport(id)}${id.name}"
        case _: IdentifierId => if(forMap) "string" else s"${if (forAlias) "" else "*"}${im.withImport(id)}${id.name}"
        case _: AdtId | _: DTOId => s"${if (forAlias) "" else "*"}${im.withImport(id)}${id.name}"
        case al: AliasId => if (isPrimitive(ts.dealias(al))) id.name else s"${if (forAlias) "" else "*"}${im.withImport(id)}${id.name}"
        case _ => throw new IDLException(s"Impossible renderUserType ${id.name}")
      }
    }
  }

  private def renderUnmarshalShared(in: String, out: String, errorCheck: Boolean): String = {
    if (!errorCheck) {
      return s"json.Unmarshal($in, $out)"
    }

    s"""if err := json.Unmarshal($in, $out); err != nil {
       |    return err
       |}
     """.stripMargin
  }

  def renderUnmarshal(content: String, assignLeft: String, assignRight: String = ""): String = {
    val tempContent = s"m${content.capitalize}"

    id match {
      case _: InterfaceId =>
        s"""var rawMap${content.capitalize} map[string]json.RawMessage
           |if err := json.Unmarshal($content, &rawMap${content.capitalize}); err != nil {
           |    return err
           |}
           |$tempContent, err := Create${id.name}(rawMap${content.capitalize})
           |if err != nil {
           |    return err
           |}
           |$assignLeft$tempContent$assignRight
         """.stripMargin

      case _: AdtId | _: DTOId | _: IdentifierId | _: AliasId =>
        s"""$tempContent := &${renderType(forAlias = true)}{}
           |${renderUnmarshalShared(content, tempContent, errorCheck = true)}
           |$assignLeft$tempContent$assignRight
         """.stripMargin

      case g: Generic => g match {
        case _: Generic.TMap => s"Not implemented renderUnmarshal.Generic.TMap"
        case _: Generic.TList => s"Not implemented renderUnmarshal.Generic.TMap"
        case _: Generic.TOption => s"Not implemented renderUnmarshal.Generic.TMap"
        case _: Generic.TSet => s"Not implemented renderUnmarshal.Generic.TMap"
      }

      case _ => throw new IDLException("Primitive types should not be unmarshalled manually")
      // case _ => assignLeft + content + assignRight
    }
  }

  def renderToString(name: String): String = id match {
    case Primitive.TString => name
    case Primitive.TInt8 => s"strconv.FormatInt(int64($name), 10)"
    case Primitive.TInt16 => s"strconv.FormatInt(int64($name), 10)"
    case Primitive.TInt32 => s"strconv.FormatInt(int64($name), 10)"
    case Primitive.TInt64 => s"strconv.FormatInt($name, 10)"
    case Primitive.TUUID => name
    case _ => throw new IDLException(s"Should never render non int or string types to strings. Used for type ${id.name}")
  }

  def renderFromString(dest: String, src: String, unescape: Boolean): String = {
    if (unescape) {
      id match {
        case Primitive.TString => s"$dest, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}"
        case Primitive.TInt8 => s"${dest}Str, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}\n${dest}64, err := strconv.ParseInt(${dest}Str, 10, 8)\nif err != nil {\n    return err\n}\n$dest := int8(${dest}64)"
        case Primitive.TInt16 => s"${dest}Str, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}\n${dest}64, err := strconv.ParseInt(${dest}Str, 10, 16)\nif err != nil {\n    return err\n}\n$dest := int16(${dest}64)"
        case Primitive.TInt32 => s"${dest}Str, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}\n${dest}64, err := strconv.ParseInt(${dest}Str, 10, 32)\nif err != nil {\n    return err\n}\n$dest := int32(${dest}64)"
        case Primitive.TInt64 => s"${dest}Str, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}\n$dest, err := strconv.ParseInt(${dest}Str, 10, 64)\nif err != nil {\n    return err\n}"
        case Primitive.TUUID => s"$dest, err := url.QueryUnescape($src)\nif err != nil {\n    return err\n}"
        case _ => throw new IDLException(s"Should never parse non int or string types. Used for type ${id.name}")
      }
    } else {
      throw new IDLException(s"Render from string for non unescaped ones is not supported yet!")
    }
  }

  def testValue(): String = id match {
    case Primitive.TBool => "true"
    case Primitive.TString => "\"Sample String\""
    case Primitive.TInt8 => "8"
    case Primitive.TInt16 => "16"
    case Primitive.TInt32 => "32"
    case Primitive.TInt64 => "64"
    case Primitive.TFloat => "32.32"
    case Primitive.TDouble => "64.64"
    case Primitive.TUUID => "\"d71ec06e-4622-4663-abd0-de1470eb6b7d\""
    case Primitive.TTime => "time.Now()" // "\"15:10:10.10001\""
    case Primitive.TDate => "time.Now()" // "\"2010-12-01\""
    case Primitive.TTs => "time.Now()" // "\"2010-12-01T15:10:10.10001\""
    case Primitive.TTsTz => "time.Now()" // "\"2010-12-01T15:10:10.10001[UTC]\""
    case g: Generic => g match {
      case gm: Generic.TMap => s"map[${GoLangType(gm.keyType, im, ts).renderType(forMap = true)}]${GoLangType(gm.valueType, im, ts).renderType()}{}"
      case gl: Generic.TList => s"[]${GoLangType(gl.valueType, im, ts).renderType()}{}"
      case gs: Generic.TSet => s"[]${GoLangType(gs.valueType, im, ts).renderType()}{}"
      case _: Generic.TOption => "nil"
    }
    case al: AliasId => GoLangType(ts(al).asInstanceOf[Alias].target, im, ts).testValue()
    case _: IdentifierId | _: DTOId | _: EnumId => s"${im.withImport(id)}NewTest${id.name}()"
    case i: InterfaceId => s"${im.withImport(id)}NewTest${i.name + ts.implId(i).name}()"
    case _ => "nil"
  }

  def testValuePackage(): List[String] = id match {
    case Primitive.TTime | Primitive.TDate | Primitive.TTsTz | Primitive.TTs => List("time")
      // TODO For testing we might want to import from other packages...
//    case al: AliasId => GoLangType(ts(al).asInstanceOf[Alias].target, im, ts).testValuePackage()
//    case _: IdentifierId | _: DTOId | _: EnumId => s"NewTest${id.name}()"
//    case i: InterfaceId => s"NewTest${i.name + ts.implId(i).name}()"
    case _ => List.empty
  }
}

object GoLangType {
  def apply(
             id: TypeId,
             im: GoLangImports = GoLangImports(List.empty),
             ts: Typespace = null
           ): GoLangType = new GoLangType(id, im, ts)
}
