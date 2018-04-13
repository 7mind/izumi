package com.github.pshirshov.izumi.idealingua.translator.totypescript.types

import com.github.pshirshov.izumi.idealingua.model.common.Generic.{TList, TMap, TOption, TSet}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Field
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace


class TypeScriptTypeConverter(domain: DomainId) {
  def parseTypeFromString(value: String, target: TypeId): String = {
    target match {
      case Primitive.TBool => "(" + value + " === 'true)'"
      case Primitive.TString => value
      case Primitive.TInt8 => "parseInt(" + value + ", 10)"
      case Primitive.TInt16 => "parseInt(" + value + ", 10)"
      case Primitive.TInt32 => "parseInt(" + value + ", 10)"
      case Primitive.TInt64 => "parseInt(" + value + ", 10)"
      case Primitive.TFloat => "parseFloat(" + value + ")"
      case Primitive.TDouble => "parseFloat(" + value + ")"
      case Primitive.TUUID => value
      case Primitive.TTime => "Date.parse(" + value + ")"
      case Primitive.TDate => "Date.parse(" + value + ")"
      case Primitive.TTs => "Date.parse(" + value + ")"
      case Primitive.TTsTz => "Date.parse(" + value + ")"
      // TODO We do nothing for other types, should probably figure something out ...
      case _ => value
    }
  }

  def deserializeName(name: String, target: TypeId): String = target match {
    case Primitive.TTime => name + "AsString"
    case Primitive.TDate => name + "AsString"
    case _ => name
  }

  def deserializeType(variable: String, target: TypeId, ts: Typespace, asAny: Boolean = false): String = {
    target match {
      case Primitive.TBool => variable
      case Primitive.TString => variable
      case Primitive.TInt8 => variable
      case Primitive.TInt16 => variable
      case Primitive.TInt32 => variable
      case Primitive.TInt64 => variable
      case Primitive.TFloat => variable
      case Primitive.TDouble => variable
      case Primitive.TUUID => variable
      case Primitive.TTime => variable
      case Primitive.TDate => variable
      case Primitive.TTs => "new Date(" + variable + ")"
      case Primitive.TTsTz => "new Date(" + variable + ")"
      case g: Generic => deserializeGenericType(variable, g, ts, asAny)
      case _ => deserializeCustomType(variable, target, ts, asAny)
    }
  }

  def deserializeGenericType(variable: String, target: Generic, ts: Typespace, asAny: Boolean = false): String = target match {
    case gm: Generic.TMap => s"Object.keys($variable).reduce((previous, current) => {previous[current] = ${deserializeType(s"$variable[current]", gm.valueType, ts, asAny)}; return previous; }, {})"
    case gl: Generic.TList => gl.valueType match {
      case _: Primitive => s"$variable.slice()"
      case _ => s"$variable.map(e => { return ${deserializeType("e", gl.valueType, ts, asAny)}; })"
    }
    case go: Generic.TOption => s"typeof $variable !== 'undefined' ? ${deserializeType(variable, go.valueType, ts, asAny)} : undefined"
    case gs: Generic.TSet => gs.valueType match {
      case _: Primitive => s"$variable.slice()"
      case _ => s"$variable.map(e => { return ${deserializeType("e", gs.valueType, ts, asAny)}; })"
    }
  }

  def deserializeCustomType(variable: String, target: TypeId, ts: Typespace, asAny: Boolean = false): String = target match {
    case a: AdtId => s"${a.name}Helpers.deserialize($variable)"
    case i: InterfaceId => s"${i.name}Struct.create(${variable + (if (asAny) " as any" else "")})"
    case d: DTOId => s"new ${d.name}(${variable + (if (asAny) " as any" else "")})"
    case al: AliasId => {
      val alias = ts(al).asInstanceOf[Alias]
      deserializeType(variable, alias.target, ts)
    }
    case id: IdentifierId => s"new ${id.name}($variable)"
    case _: EnumId => s"$variable"

    case _ => s"'$variable: Error here! Not Implemented! ${target.name}'"
  }

  def toNativeType(id: TypeId, forSerialized: Boolean = false, ts: Typespace = null): String = {
    id match {
      case t: Generic => toGenericType(t, forSerialized, ts)
      case t: Primitive => toPrimitiveType(t, forSerialized)
      case _ => toCustomType(id, forSerialized, ts)
    }
  }

  def toNativeTypeName(name: String, id: TypeId): String = id match {
    case t: Generic => t match {
      case _: Generic.TOption => name + "?"
      case _ => name
    }
    case _ => name
  }

  def toCustomType(id: TypeId, forSerialized: Boolean = false, ts: Typespace = null): String = {
    if (forSerialized) {
      id match {
        case i: InterfaceId => s"{[key: string]: ${ts.implId(i).name + "Serialized"}}"
        case a: AdtId => s"{[key: string]: ${a.name}}"
        case _: IdentifierId => s"string"
        case _ => s"${id.name}"
      }
    } else {
      id.name
    }
  }

  private def toPrimitiveType(id: Primitive, forSerialized: Boolean = false): String = id match {
    case Primitive.TBool => "boolean"
    case Primitive.TString => "string"
    case Primitive.TInt8 => "number"
    case Primitive.TInt16 => "number"
    case Primitive.TInt32 => "number"
    case Primitive.TInt64 => "number"
    case Primitive.TFloat => "number"
    case Primitive.TDouble => "number"
    case Primitive.TUUID => "string"
    case Primitive.TTime => if (forSerialized) "string" else "Date"
    case Primitive.TDate => if (forSerialized) "string" else "Date"
    case Primitive.TTs => if (forSerialized) "string" else "Date"
    case Primitive.TTsTz => if (forSerialized) "string" else "Date"
  }

  private def toGenericType(typeId: Generic, forSerialized: Boolean = false, ts: Typespace = null): String = {
    typeId match {
      case _: Generic.TSet => toNativeType(typeId.asInstanceOf[TSet].valueType, forSerialized, ts) + "[]"
      case _: Generic.TMap => "{[key: " + toNativeType(typeId.asInstanceOf[TMap].keyType) + "]: " + toNativeType(typeId.asInstanceOf[TMap].valueType, forSerialized, ts) + "}"
      case _: Generic.TList => toNativeType(typeId.asInstanceOf[TList].valueType, forSerialized, ts) + "[]"
      case _: Generic.TOption => toNativeType(typeId.asInstanceOf[TOption].valueType, forSerialized, ts)
    }
  }

  def serializeField(field: Field, ts: Typespace): String = {
    s"'${field.name}': ${serializeValue("this." + field.name, field.typeId, ts)}"
  }

  def serializeValue(name: String, id: TypeId, ts: Typespace): String = id match {
    case p: Primitive => serializePrimitive(name, p, ts)
    case _: Generic => serializeGeneric(name, id, ts)
    case _ => serializeCustom(name, id, ts)
  }

  def serializePrimitive(name: String, id: Primitive, ts: Typespace): String = id match {
    case Primitive.TBool => s"$name"
    case Primitive.TString => s"$name"
    case Primitive.TInt8 => s"$name"
    case Primitive.TInt16 => s"$name"
    case Primitive.TInt32 => s"$name"
    case Primitive.TInt64 => s"$name"
    case Primitive.TFloat => s"$name"
    case Primitive.TDouble => s"$name"
    case Primitive.TUUID => s"$name"
    case Primitive.TTime => s"${name}AsString"
    case Primitive.TDate => s"${name}AsString"
    case Primitive.TTs => s"$name.toISOString()"
    case Primitive.TTsTz => s"$name.toISOString()"
  }

  def serializeGeneric(name: String, id: TypeId, ts: Typespace): String = id match {
    case m: Generic.TMap => s"Object.keys($name).reduce((previous, current) => {previous[current] = ${serializeValue(s"$name[current]", m.valueType, ts)}; return previous; }, {})"
    case s: Generic.TSet => s.valueType match {
      case _: Primitive => s"$name.slice()"
      case _ => s"$name.map(e => { return ${serializeValue("e", s.valueType, ts)}; })"
    }
    case l: Generic.TList => l.valueType match {
      case _: Primitive => s"$name.slice()"
      case _ => s"$name.map(e => { return ${serializeValue("e", l.valueType, ts)}; })"
    }
    case o: Generic.TOption => s"typeof $name !== 'undefined' ? ${serializeValue(name, o.valueType, ts)} : undefined"
    case _ => s"$name: 'Error here! Not Implemented!'"
  }

  def serializeCustom(name: String, id: TypeId, ts: Typespace): String = id match {
    case a: AdtId => s"${a.name}Helpers.serialize($name)"
    case _: InterfaceId => s"{[$name.getFullClassName()]: $name.serialize()}"
    case d: DTOId => s"${d.name}.serialize()"
    case al: AliasId => {
      val alias = ts(al).asInstanceOf[Alias]
      serializeValue(name, alias.target, ts)
    }
    case _: IdentifierId => s"$name.serialize()"
    case _: EnumId => s"$name"

    case _ => s"'$name: Error here! Not Implemented! ${id.name}'"
  }

  def toFieldMethods(id: TypeId, name: String, optional: Boolean = false) = id match {
    case Primitive.TBool => toBooleanField(name, optional)
    case Primitive.TString => toStringField(name, Int.MinValue, optional)
    case Primitive.TInt8 => toIntField(name, -128, 127, optional)
    case Primitive.TInt16 => toIntField(name, -32768, 32767, optional)
    case Primitive.TInt32 => toIntField(name, -2147483648, 2147483647, optional)
    case Primitive.TInt64 => toIntField(name, Int.MinValue, Int.MaxValue, optional)
    case Primitive.TFloat => toDoubleField(name, 32, optional)
    case Primitive.TDouble => toDoubleField(name, 64, optional)
    case Primitive.TUUID => toGuidField(name, optional)
    case Primitive.TTime => toTimeField(name, optional)
    case Primitive.TDate => toDateField(name, optional)
    case Primitive.TTs => toDateField(name, optional)
    case Primitive.TTsTz => toDateField(name, optional)
    case _ =>
      s"""public get ${safeName(name)}(): ${toNativeType(id)} {
         |    return this._$name;
         |}
         |
         |public set ${safeName(name)}(value: ${toNativeType(id)}) {
         |    if (typeof value === 'undefined' || value === null) {
         |        ${if (optional) s"this._$name = undefined;\n        return;" else s"throw new Error('Field ${safeName(name)} is not optional');"}
         |    }
         |    this._$name = value;
         |}
       """.stripMargin
  }

  def toFieldMethods(field: Field): String = field.typeId match {
    case go: Generic.TOption => toFieldMethods(go.valueType, field.name, true)
    case _ => toFieldMethods(field.typeId, field.name, false)
  }

  def toFieldMember(field: Field): String = {
    toFieldMember(field.name, field.typeId)
  }

  def toFieldMember(name: String, id: TypeId): String = id match {
    case Primitive.TBool => toPrivateMember(name, "boolean")
    case Primitive.TString => toPrivateMember(name, "string")
    case Primitive.TInt8 => toPrivateMember(name, "number")
    case Primitive.TInt16 => toPrivateMember(name, "number")
    case Primitive.TInt32 => toPrivateMember(name, "number")
    case Primitive.TInt64 => toPrivateMember(name, "number")
    case Primitive.TFloat => toPrivateMember(name, "number")
    case Primitive.TDouble => toPrivateMember(name, "number")
    case Primitive.TUUID => toPrivateMember(name, "string")
    case Primitive.TTime => toPrivateMember(name, "Date")
    case Primitive.TDate => toPrivateMember(name, "Date")
    case Primitive.TTs => toPrivateMember(name, "Date")
    case Primitive.TTsTz => toPrivateMember(name, "Date")
    case _ => toPrivateMember(name, toNativeType(id))
  }

  def safeName(name: String): String = {
    val ecma1 = Seq("do", "if", "in", "for", "let", "new", "try", "var", "case", "else", "enum", "eval", "null", "as",
      "this", "true", "void", "with", "await", "break", "catch", "class", "const", "false", "super", "throw", "while",
      "yield", "delete", "export", "import", "public", "return", "static", "switch", "typeof", "default", "extends",
      "finally", "package", "private", "continue", "debugger", "function", "arguments", "interface", "protected", "any",
      "implements", "instanceof", "namespace", "boolean", "constructor", "declare", "get", "module", "require",
      "number", "set", "string", "symbol", "type", "from", "of")

    //    if (ecma1.contains(name)) s"m${name.capitalize}" else name
    name
  }

  def toPrivateMember(name: String, memberType: String): String = {
    s"private _$name: $memberType;"
  }

  def toIntField(name: String, min: Int = Int.MinValue, max: Int = Int.MaxValue, optional: Boolean = false): String = {
    var base =
      s"""public get ${safeName(name)}(): number {
         |    return this._$name;
         |}
         |
         |public set ${safeName(name)}(value: number) {
         |    if (typeof value === 'undefined' || value === null) {
         |        ${if (optional) s"this._$name = undefined;\n        return;" else s"throw new Error('Field ${safeName(name)} is not optional');"}
         |    }
         |
         |    if (typeof value !== 'number') {
         |        throw new Error('Field ${safeName(name)} expects type number, got ' + value);
         |    }
         |
         |    if (value % 1 !== 0) {
         |        throw new Error('Field ${safeName(name)} is expected to be an integer, got ' + value);
         |    }
       """ // value = ~~value; <- alternative to drop the decimal part

    if (min != Int.MinValue) {
      base +=
        s"""
           |    if (value < $min) {
           |        throw new Error('Field ${safeName(name)} is expected to be not less than $min, got ' + value);
           |    }
         """
    }

    if (max != Int.MaxValue) {
      base +=
        s"""
           |    if (value > $max) {
           |        throw new Error('Field ${safeName(name)} is expected to be not greater than $max, got ' + value);
           |    }
         """
    }

    base +=
      s"""
         |    this._$name = value;
         |}
       """
    base.stripMargin
  }

  def toDoubleField(name: String, precision: Int = 64, optional: Boolean = false): String = {
    s"""public get ${safeName(name)}(): number {
       |    return this._$name;
       |}
       |
       |public set ${safeName(name)}(value: number) {
       |    if (typeof value === 'undefined' || value === null) {
       |        ${if (optional) s"this._$name = undefined;\n        return;" else s"throw new Error('Field ${safeName(name)} is not optional');"}
       |    }
       |
       |    if (typeof value !== 'number') {
       |        throw new Error('Field ${safeName(name)} expects type number, got ' + value);
       |    }
       |
       |    this._$name = value;
       |}
     """.stripMargin
  }

  def toBooleanField(name: String, optional: Boolean = false): String = {
    s"""public get ${safeName(name)}(): boolean {
       |    return this._$name;
       |}
       |
       |public set ${safeName(name)}(value: boolean) {
       |    if (typeof value === 'undefined' || value === null) {
       |        ${if (optional) s"this._$name = undefined;\n        return;" else s"throw new Error('Field ${safeName(name)} is not optional');"}
       |    }
       |
       |    if (typeof value !== 'boolean') {
       |        throw new Error('Field ${safeName(name)} expects boolean type, got ' + value);
       |    }
       |
       |    this._$name = value;
       |}
     """.stripMargin
  }

  def toGuidField(name: String, optional: Boolean = false): String = {
    s"""public get ${safeName(name)}(): string {
       |    return this._$name;
       |}
       |
       |public set ${safeName(name)}(value: string) {
       |    if (typeof value === 'undefined' || value === null) {
       |        ${if (optional) s"this._$name = undefined;\n        return;" else s"throw new Error('Field ${safeName(name)} is not optional');"}
       |    }
       |
       |    if (typeof value !== 'string') {
       |        throw new Error('Field ${safeName(name)} expects type string, got ' + value);
       |    }
       |
       |    if (!value.match('^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$$')) {
       |        throw new Error('Field ${safeName(name)} expects guid format, got ' + value);
       |    }
       |
       |    this._$name = value;
       |}
     """.stripMargin
  }

  def toStringField(name: String, max: Int = Int.MinValue, optional: Boolean = false): String = {
    var base =
      s"""public get ${safeName(name)}(): string {
         |    return this._$name;
         |}
         |
         |public set ${safeName(name)}(value: string) {
         |    if (typeof value === 'undefined' || value === null) {
         |        ${if (optional) s"this._$name = undefined;\n        return;" else s"throw new Error('Field ${safeName(name)} is not optional');"}
         |    }
         |
         |    if (typeof value !== 'string') {
         |        throw new Error('Field ${safeName(name)} expects type string, got ' + value);
         |    }
     """

    if (max > 0) {
      base +=
        s"""
           |    if (value.length > $max) {
           |        value = value.substr(0, $max);
           |    }
         """
    }

    base +=
      s"""
         |    this._$name = value;
         |}
       """
    base.stripMargin
  }

  def toDateField(name: String, optional: Boolean = false): String = {
    s"""public get ${safeName(name)}(): Date {
       |    return this._$name;
       |}
       |
       |public set ${safeName(name)}(value: Date) {
       |    if (typeof value === 'undefined' || value === null) {
       |        ${if (optional) s"this._$name = undefined;\n        return;" else s"throw new Error('Field ${safeName(name)} is not optional');"}
       |    }
       |
       |    if (!(value instanceof Date)) {
       |        throw new Error('Field ${safeName(name)} expects type Date, got ' + value);
       |    }
       |    this._$name = value;
       |}
       |
       |public get ${safeName(name)}AsString(): string {
       |    return this._$name.getDate() + ':' + this._$name.getMonth() + ':' + this._$name.getFullYear();
       |}
       |
       |public set ${safeName(name)}AsString(value: string) {
       |    if (typeof value !== 'string') {
       |        throw new Error('${safeName(name)}AsString expects type string, got ' + value);
       |    }
       |    this._$name = new Date(value);
       |}
     """.stripMargin
  }

  def toTimeField(name: String, optional: Boolean = false): String = {
    s"""public get ${safeName(name)}(): Date {
       |    return this._$name;
       |}
       |
       |public set ${safeName(name)}(value: Date) {
       |    if (typeof value === 'undefined' || value === null) {
       |        ${if (optional) s"this._$name = undefined;\n        return;" else s"throw new Error('Field ${safeName(name)} is not optional');"}
       |    }
       |
       |    if (!(value instanceof Date)) {
       |        throw new Error('Field ${safeName(name)} expects type Date, got ' + value);
       |    }
       |
       |    this._$name = value;
       |}
       |
       |public get ${safeName(name)}AsString(): string {
       |    return this._$name.getHours() + ':' + this._$name.getMinutes() + ':' + this._$name.getSeconds() + '.' + this._$name.getMilliseconds();
       |}
       |
       |public set ${safeName(name)}AsString(value: string) {
       |    if (typeof value !== 'string') {
       |        throw new Error('${safeName(name)}AsString expects type string, got ' + value);
       |    }
       |
       |    const parts = value.split(':');
       |    if (parts.length !== 3) {
       |        throw new Error('Field ${safeName(name)} expects time to be in the format HH:MM:SS.ms, got ' + value);
       |    }
       |
       |    const time = new Date();
       |    time.setHours(parseInt(parts[0]), parseInt(parts[1]));
       |    if (parts[2].indexOf('.') >= 0) {
       |        const parts2 = parts[2].split('.');
       |        if (parts2.length !== 2) {
       |            throw new Error('Field ${safeName(name)} expects time to be in the format HH:MM:SS.ms, got ' + value);
       |        }
       |
       |        time.setSeconds(parseInt(parts2[0]));
       |        time.setMilliseconds(parseInt(parts2[1].substr(0, 3)));
       |    } else {
       |        time.setSeconds(parseInt(parts[2]));
       |    }
       |
       |    this._$name = time;
       |}
     """.stripMargin
  }
}
