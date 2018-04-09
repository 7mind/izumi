package com.github.pshirshov.izumi.idealingua.translator.totypescript.types

import com.github.pshirshov.izumi.idealingua.model.common.Generic.{TList, TMap, TOption, TSet}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, AliasId, DTOId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{DomainId, Field}
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

import scala.language.higherKinds

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

  def deserializeType(variable: String, target: TypeId, ts: Typespace): String = {
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
      case Primitive.TTime => "new Date(" + variable + ".getTime())"
      case Primitive.TDate => "new Date(" + variable + ".getTime())"
      case Primitive.TTs => "new Date(" + variable + ".getTime())"
      case Primitive.TTsTz => "new Date(" + variable + ".getTime())"
      case g: Generic => deserializeGenericType(variable, g, ts)
      case _ => deserializeCustomType(variable, target, ts)
    }
  }

  def deserializeGenericType(variable: String, target: Generic, ts: Typespace): String = target match {
    case gm: Generic.TMap => s"Object.keys(${variable}).reduce((previous, current) => {previous[current] = ${deserializeType(s"${variable}[current]", gm.valueType, ts)}; return previous; }, {})"
    case gl: Generic.TList => gl.valueType match {
      case _: Primitive => s"${variable}.slice()"
      case _ => s"${variable}.map(e => ${serializeValue("e", gl.valueType, ts)})"
    }
    case go: Generic.TOption => s"typeof ${variable} !== 'undefined' ? ${deserializeType(variable, go.valueType, ts)} : undefined"
    case gs: Generic.TSet => gs.valueType match {
      case _: Primitive => s"${variable}.slice()"
      case _ => s"${variable}.map(e => ${serializeValue("e", gs.valueType, ts)})"
    }
  }

  def deserializeCustomType(variable: String, target: TypeId, ts: Typespace): String = target match {
    case a: AdtId => s"${a.name}Helpers.deserialize(${variable})"
    case i: InterfaceId => s"${i.name}Struct.create(${variable})"
    case d: DTOId => s"new ${d.name}(${variable})"
    case al: AliasId => {
      val alias = ts(al).asInstanceOf[Alias]
      deserializeType(variable, alias.target, ts)
    }

    case _ => s"'${variable}: Error here! Not Implemented! ${target.name}'"
  }

  def toNativeType(id: TypeId, forInterface: Boolean = false): String = {
    id match {
      case t: Generic => toGenericType(t, forInterface)
      case t: Primitive => toPrimitiveType(t, forInterface)
      case _ => toCustomType(id, forInterface)
    }
  }

  def toCustomType(id: TypeId, forInterface: Boolean = false): String = {
    if (forInterface) {
      id match {
        case i: InterfaceId => s"{[key: string]: ${i.name}}"
        case a: AdtId => s"{[key: string]: ${a.name}}"
        case _ => s"${id.name}"
      }
    } else {
      id.name
    }
  }

  private def toPrimitiveType(id: Primitive, forInterface: Boolean = false): String = id match {
    case Primitive.TBool => "boolean"
    case Primitive.TString => "string"
    case Primitive.TInt8 => "number"
    case Primitive.TInt16 => "number"
    case Primitive.TInt32 => "number"
    case Primitive.TInt64 => "number"
    case Primitive.TFloat => "number"
    case Primitive.TDouble => "number"
    case Primitive.TUUID => "string"
    case Primitive.TTime => "Date"
    case Primitive.TDate => "Date"
    case Primitive.TTs => "Date"
    case Primitive.TTsTz => "Date"
  }

  private def toGenericType(typeId: Generic, forInterface: Boolean = false): String = {
    typeId match {
      case _: Generic.TSet => "Set<" + toNativeType(typeId.asInstanceOf[TSet].valueType) + ">"
      case _: Generic.TMap => "{[key: " + toNativeType(typeId.asInstanceOf[TMap].keyType) + "]: " + toNativeType(typeId.asInstanceOf[TMap].valueType) + "}"
      case _: Generic.TList => toNativeType(typeId.asInstanceOf[TList].valueType) + "[]"
      case _: Generic.TOption => toNativeType(typeId.asInstanceOf[TOption].valueType) + "?"
    }
  }

  def serializeField(field: Field, ts: Typespace): String = {
    s"${field.name}: ${serializeValue("this." + field.name, field.typeId, ts)}"
  }

  def serializeValue(name: String, id: TypeId, ts: Typespace): String = id match {
    case _: Primitive => serializePrimitive(name, id, ts)
    case _: Generic => serializeGeneric(name, id, ts)
    case _ => serializeCustom(name, id, ts)
  }

  def serializePrimitive(name: String, id: TypeId, ts: Typespace): String = id match {
    case Primitive.TBool => s"${name}"
    case Primitive.TString => s"${name}"
    case Primitive.TInt8 => s"${name}"
    case Primitive.TInt16 => s"${name}"
    case Primitive.TInt32 => s"${name}"
    case Primitive.TInt64 => s"${name}"
    case Primitive.TFloat => s"${name}"
    case Primitive.TDouble => s"${name}"
    case Primitive.TUUID => s"${name}"
    case Primitive.TTime => s"${name}.getHours() + ':' + ${name}.getMinutes() + ':' + ${name}.getSeconds() + '.' + ${name}.getMilliseconds()" // TODO We need to properly format milliseconds here
    case Primitive.TDate => s"${name}.toISOString().substr(0, 10)"
    case Primitive.TTs => s"${name}.toISOString()"
    case Primitive.TTsTz => s"${name}.toISOString()"
  }

  def serializeGeneric(name: String, id: TypeId, ts: Typespace): String = id match {
      case m: Generic.TMap => s"Object.keys(${name}).reduce((previous, current) => {previous[current] = ${serializeValue(s"${name}[current]", m.valueType, ts)}; return previous; }, {})"
      case s: Generic.TSet => s.valueType match {
        case _: Primitive => s"${name}.slice()"
        case _ =>  s"${name}.map(e => ${serializeValue("e", s.valueType, ts)})"
      }
      case l: Generic.TList => l.valueType match {
        case _: Primitive => s"${name}.slice()"
        case _ =>  s"${name}.map(e => ${serializeValue("e", l.valueType, ts)})"
      }
      case o: Generic.TOption => s"typeof ${name} !== 'undefined' ? ${serializeValue(name, o.valueType, ts)} : undefined"
      case _ => s"${name}: 'Error here! Not Implemented!'"
  }

  def serializeCustom(name: String, id: TypeId, ts: Typespace): String = id match {
    case a: AdtId => s"${a.name}Helpers.serialize(${name})"
    case i: InterfaceId => s"'${i.name}: {[this.${i.name}.getFullClassName()]: this.${i.name}.serialize()}"
    case d: DTOId => s"${d.name}.serialize()"
    case al: AliasId => {
      val alias = ts(al).asInstanceOf[Alias]
      serializeValue(name, alias.target, ts)
    }

    case _ => s"'${name}: Error here! Not Implemented! ${id.name}'"
  }

  def toFieldMethods(field: Field): String = field.typeId match {
    case Primitive.TBool => toBooleanField(field.name)
    case Primitive.TString => toStringField(field.name)
    case Primitive.TInt8 => toIntField(field.name, -128, 127)
    case Primitive.TInt16 => toIntField(field.name, -32768, 32767)
    case Primitive.TInt32 => toIntField(field.name, -2147483648, 2147483647)
    case Primitive.TInt64 => toIntField(field.name)
    case Primitive.TFloat => toDoubleField(field.name, 32)
    case Primitive.TDouble => toDoubleField(field.name)
    case Primitive.TUUID => toGuidField(field.name)
    case Primitive.TTime => toTimeField(field.name)
    case Primitive.TDate => toDateField(field.name)
    case Primitive.TTs => toDateField(field.name)
    case Primitive.TTsTz => toDateField(field.name)
    case _ =>
      s"""public get ${field.name}(): ${toNativeType(field.typeId)} {
         |    return this._${field.name};
         |}
         |
         |public set ${field.name}(value: ${toNativeType(field.typeId)}) {
         |    this._${field.name} = value;
         |}
       """.stripMargin
  }

  def toFieldMember(field: Field): String = field.typeId match {
    case Primitive.TBool => toPrivateMember(field.name, "boolean")
    case Primitive.TString => toPrivateMember(field.name, "string")
    case Primitive.TInt8 => toPrivateMember(field.name, "number")
    case Primitive.TInt16 => toPrivateMember(field.name, "number")
    case Primitive.TInt32 => toPrivateMember(field.name, "number")
    case Primitive.TInt64 => toPrivateMember(field.name, "number")
    case Primitive.TFloat => toPrivateMember(field.name, "number")
    case Primitive.TDouble => toPrivateMember(field.name, "number")
    case Primitive.TUUID => toPrivateMember(field.name, "string")
    case Primitive.TTime => toPrivateMember(field.name, "Date")
    case Primitive.TDate => toPrivateMember(field.name, "Date")
    case Primitive.TTs => toPrivateMember(field.name, "number")
    case Primitive.TTsTz => toPrivateMember(field.name, "number")
    case _ => toPrivateMember(field.name, toNativeType(field.typeId))
  }

  def toPrivateMember(name: String, memberType: String): String = {
    s"private _${name}: ${memberType};"
  }

  def toIntField(name: String, min: Int = Int.MinValue, max: Int = Int.MaxValue, defaultValue: String = "undefined"): String = {
    var base =
      s"""public get ${name}(): number {
         |    return this._${name};
         |}
         |
         |public set ${name}(value: number) {
         |    if (typeof value !== 'number') {
         |        this._${name} = ${defaultValue};
         |        return;
         |    }
         |
         |    if (n % 1 !== 0) {
         |        value = ~~value;
         |    }
       """

    if (min != Int.MinValue) {
      base +=
        s"""
           |    if (value <= ${min}) {
           |        value = ${min};
           |    }
         """
    }

    if (max != Int.MaxValue) {
      base +=
        s"""
           |    if (value >= ${max}) {
           |        value = ${max};
           |    }
         """
    }

    base +=
      s"""
         |    this._${name} = value;
         |}
       """
    base.stripMargin
  }

  def toDoubleField(name: String, precision: Int = 64, defaultValue: String = "undefined"): String = {
    s"""public get ${name}(): number {
       |    return this._${name};
       |}
       |
       |public set ${name}(value: number) {
       |    if (typeof value !== 'number') {
       |        this._${name} = ${defaultValue};
       |        return;
       |    }
       |
       |    this._${name} = value;
       |}
     """.stripMargin
  }

  def toBooleanField(name: String, defaultValue: String = "undefined"): String = {
    s"""public get ${name}(): boolean {
       |    return this._${name};
       |}
       |
       |public set ${name}(value: boolean) {
       |    if (typeof value !== 'boolean') {
       |        this._${name} = ${defaultValue};
       |        return;
       |    }
       |
       |    this._${name} = value;
       |}
     """.stripMargin
  }

  def toGuidField(name: String, defaultValue: String = "undefined"): String = {
    s"""public get ${name}(): string {
       |    return this._${name};
       |}
       |
       |public set ${name}(value: string) {
       |    if (typeof value !== 'string' || !value.matches('^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$$')) {
       |        this._${name} = ${defaultValue};
       |        return;
       |    }
       |
       |    this._${name} = value;
       |}
     """.stripMargin
  }

  def toStringField(name: String, max: Int = Int.MinValue, defaultValue: String = "undefined"): String = {
    var base =
      s"""public get ${name}(): string {
         |    return this._${name};
         |}
         |
       |public set ${name}(value: string) {
         |    if (typeof value !== 'string') {
         |        this._${name} = ${defaultValue};
         |        return;
         |    }
     """

    if (max > 0) {
      base +=
        s"""
           |    if (value.length > ${max}) {
           |        value = value.substr(0, ${max});
           |    }
         """
    }

    base +=
      s"""
         |    this._${name} = value;
         |}
       """
    base.stripMargin
  }

  def toDateField(name: String, defaultValue: String = "undefined"): String = {
    s"""public get ${name}(): Date {
       |    return this._${name};
       |}
       |
       |public set ${name}(value: Date | string) {
       |    if (typeof value !== 'string' && !(value instanceof Date)) {
       |        this._${name} = ${defaultValue};
       |        return;
       |    }
       |
       |    if (typeof value === 'string') {
       |        this._${name} = Date.parse(value);
       |    } else {
       |        this._${name} = value;
       |    }
       |}
     """.stripMargin
  }

  def toTimeField(name: String, defaultValue: String = "undefined"): String = {
    s"""public get ${name}(): Date {
       |    return this._${name};
       |}
       |
       |public set ${name}(value: Date | string): void {
       |    if (typeof value !== 'string' && !(value instanceof Date)) {
       |        this._${name} = ${defaultValue};
       |        return;
       |    }
       |
       |    if (typeof value === 'string') {
       |        const parts = value.split(':');
       |        if (parts.length !== 3) {
       |            this._${name} = ${defaultValue};
       |            return;
       |        }
       |
       |        const time = new Date();
       |        time.setHours(parseInt(parts[0]), parseInt(parts[1]));
       |        if (parts[2].indexOf('.') >= 0) {
       |            const parts2 = parts[2].split('.');
       |            if (parts2.length !== 2) {
       |                this._${name} = ${defaultValue};
       |                return;
       |            }
       |
       |            time.setSeconds(parseInt(parts2[0]));
       |            time.setMilliseconds(parseInt(parts2[1].substr(0, 3));
       |        } else {
       |            time.setSeconds(parseInt(parts[2]));
       |        }
       |        this._${name} = time;
       |    } else {
       |        this._${name} = value;
       |    }
       |}
     """.stripMargin
  }
}
