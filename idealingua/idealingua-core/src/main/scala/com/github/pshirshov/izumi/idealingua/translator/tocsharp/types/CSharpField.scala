package com.github.pshirshov.izumi.idealingua.translator.csharp.types

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Field
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpImports
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.types.CSharpType

final case class CSharpField(
                              name: String,
                              tp: CSharpType,
                              structName: String
                            ) (implicit im: CSharpImports, ts: Typespace) {
  def renderMemberName(): String = {
    safeName(name)
  }

  protected def safeName(name: String): String = {
    val reserved = Seq("abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked",
      "class", "const", "continue", "decimal", "default", "delegate", "do", "double", "else", "enum", "event",
      "explicit", "extern", "false", "finally", "fixed", "float", "for", "foreach", "goto", "if", "implicit", "in",
      "int", "interface", "internal", "is", "lock", "long", "namespace", "new", "null", "object", "operator", "out",
      "override", "params", "private", "protected", "public", "readonly", "ref", "return", "sbyte", "sealed",
      "short", "sizeof", "stackalloc", "static", "string", "struct", "switch", "this", "throw", "true", "try",
      "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort", "using", "using", "static", "virtual", "void",
      "volatile", "while")
    /*
      Contextual words in C#, not reserved:
      add	alias	ascending
      async	await	descending
      dynamic	from	get
      global	group	into
      join	let	nameof
      orderby	partial (type)	partial (method)
      remove	select	set
      value	var	when (filter condition)
      where (generic type constraint)	where (query clause)	yield
     */

    val finalName = name.capitalize
    return if (reserved.contains(name)) s"@$finalName" else finalName
  }

  def renderMember(): String = {
    if (tp.isNative()) {
      s"""public ${tp.renderType()} ${renderMemberName()} { get; set; }
       """.stripMargin
    } else {
      s"""Not Implemented renderMember()""".stripMargin
    }
  }

//
//  private def renderPolymorphSerialized(id: TypeId, dest: String, srcOriginal: String, forOption: Boolean = false): String = {
//    val src =  if (forOption) s"(*$srcOriginal)" else srcOriginal
//
//    id match {
//      case _: InterfaceId =>
//        s"""_$dest, err := json.Marshal($src)
//           |if err != nil {
//           |    return nil, err
//           |}
//           |$dest ${if(forOption) "" else ":"}= ${if(forOption) "&" else ""}map[string]json.RawMessage{$src.GetFullClassName(): _$dest}
//         """.stripMargin
//
//      case _: AdtId =>
//        s"""_$dest, err := json.Marshal($src)
//           |if err != nil {
//           |    return nil, err
//           |}
//           |$dest ${if(forOption) "" else ":"}= ${if(forOption) s"interface{}(&_$dest).(*json.RawMessage) // A bit hacky, but the underlying type for json.RawMessage is []byte, so should be legit" else s"_$dest"}
//         """.stripMargin
//
//      case _: IdentifierId | _: EnumId =>
//        s"""_$dest := $src.String()
//           |$dest ${if(forOption) "" else ":"}= ${if(forOption) "&" else ""}_$dest
//       """.stripMargin
//
//      case _: DTOId =>
//        s"""_$dest, err := $src.Serialize()
//           |if err != nil {
//           |    return nil, err
//           |}
//           |$dest ${if(forOption) "" else ":"}= ${if(forOption) "&" else ""}_$dest
//         """.stripMargin
//
//      case al: AliasId => renderPolymorphSerialized(ts.dealias(al), dest, srcOriginal, forOption)
//
//      case _ => throw new IDLException(s"Should not get into renderPolymorphSerialized ${id.name} dest: $dest src: $srcOriginal")
//    }
//  }
//
//  private def renderPolymorphSerializedVar(id: TypeId, dest: String, src: String): String = {
//    id match {
//      case _: AliasId => renderPolymorphSerializedVar(ts.dealias(id), dest, src)
//
//      case _: InterfaceId | _: AdtId | _: IdentifierId | _: DTOId | _: EnumId =>
//        renderPolymorphSerialized(id, dest, src)
//
//      case g: Generic => g match {
//        case go: Generic.TOption =>
//          s"""var $dest ${GoLangType(id, im, ts).renderType(serialized = true)} = nil
//             |if $src != nil {
//             |${
//            if (GoLangType(id, im, ts).isPolymorph(go.valueType))
//              renderPolymorphSerialized(go.valueType, dest, src, forOption = true).shift(4)
//            else
//              s"    $dest = $src"}
//             |}
//           """.stripMargin
//
//        case _: Generic.TList | _: Generic.TSet => {
//          val vt = g match {
//            case gl: Generic.TList => gl.valueType
//            case gs: Generic.TSet => gs.valueType
//            case _ => throw new IDLException("Just preventing a warning here...")
//          }
//
//          val tempVal = s"_$dest"
//
//          // TODO This is not going to work well for nested lists
//          if (GoLangType(null, im, ts).isPrimitive(vt))
//            s"$dest := $src"
//          else
//            s"""$tempVal := make(${GoLangType(id, im, ts).renderType(serialized = true)}, len($src))
//               |for ${tempVal}Index, ${tempVal}Val := range $src {
//               |${
//              if (vt.isInstanceOf[Generic])
//                renderPolymorphSerializedVar(vt, s"$tempVal[${tempVal}Index]", s"${tempVal}Val").shift(4) else
//                (renderPolymorphSerialized(vt, "__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Index] = __dest").shift(4)
//            }
//               |}
//               |$dest := $tempVal
//                 """.stripMargin
//        }
//
//        case gm: Generic.TMap => {
//          val vt = GoLangType(gm.valueType, im, ts)
//          val tempVal = s"_$dest"
//
//          if (GoLangType(null, im, ts).isPrimitive(vt.id))
//            s"$dest := $src"
//          else
//            s"""$tempVal := make(${GoLangType(gm, im, ts).renderType(serialized = true)})
//               |for ${tempVal}Key, ${tempVal}Val := range $src {
//               |${if (vt.isInstanceOf[Generic])
//              renderPolymorphSerializedVar(vt.id, s"$tempVal[${tempVal}Index]", s"${tempVal}Val").shift(4) else
//              (renderPolymorphSerialized(vt.id, s"__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Key] = __dest").shift(4)}
//               |}
//               |$dest := $tempVal
//                 """.stripMargin
//        }
//      }
//
//      case _ => throw new IDLException("Should never get into renderPolymorphSerializedVar with other types... " + id.name + " " + id.getClass().getName())
//    }
//  }
//
//  def renderPolymorphSerializedVar(dest: String, src: String): String = {
//    renderPolymorphSerializedVar(tp.id, dest, src)
//  }
//
//  def renderSerializedVar(): String = {
//    tp.id match {
//      case Primitive.TTsTz => renderMemberName(true) + "AsString()"
//      case Primitive.TTs => renderMemberName(true) + "AsString()"
//      case Primitive.TDate => renderMemberName(true) + "AsString()"
//      case Primitive.TTime => renderMemberName(true) + "AsString()"
//      case _ => renderMemberName(false)
//    }
//  }
//
//  def renderInterfaceMethods(): String = {
//    s"""${renderMemberName(true)}() ${tp.renderType()}
//       |Set${renderMemberName(true)}(value ${tp.renderType()})${if (tp.hasSetterError) " error" else ""}
//     """.stripMargin
//  }
//
//  def renderMember(): String = {
//    s"${renderMemberName(false)} ${tp.renderType()}"
//  }
//
//  def renderMethods(): String = tp.id match {
//    case Primitive.TUUID => toGuidField()
//    case Primitive.TTime => toTimeField()
//    case Primitive.TDate => toDateField()
//    case Primitive.TTs => toTimeStampField(false)
//    case Primitive.TTsTz => toTimeStampField(true)
//    case _ => toGenericField()
//  }
//
//  private def renderSetterNilCheck(): String = {
//    s"""if value == nil {
//       |    return fmt.Errorf("Set${renderMemberName(true)} is not optional")
//       |}
//     """.stripMargin
//  }
//
//  def toGenericField(): String = {
//    s"""func (v *$structName) ${renderMemberName(true)}() ${tp.renderType()} {
//       |    return v.${renderMemberName(false)}
//       |}
//       |
//       |func (v *$structName) Set${renderMemberName(true)}(value ${tp.renderType()}) ${if (tp.hasSetterError) "error " else ""}{
//       |    ${if (tp.hasSetterError) renderSetterNilCheck() else ""}
//       |    v.${renderMemberName(false)} = value${if (tp.hasSetterError) "\n    return nil" else ""}
//       |}
//     """.stripMargin
//  }
//
//  def toGuidField(): String = {
//    s"""func (v *$structName) ${renderMemberName(true)}() string {
//       |    return v.${renderMemberName(false)}
//       |}
//       |
//       |func (v *$structName) Set${renderMemberName(true)}(value string) error {
//       |    pattern := regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$$`)
//       |    matched := pattern.MatchString(value)
//       |    if !matched {
//       |        return fmt.Errorf("Set${renderMemberName(true)} expects the value to be a valid UUID. Got %s", value)
//       |    }
//       |
//       |    v.${renderMemberName(false)} = value
//       |    return nil
//       |}
//     """.stripMargin
//  }
//
//  def toDateField(): String = {
//    s"""func (v *$structName) ${renderMemberName(true)}() time.Time {
//       |    return v.${renderMemberName(false)}
//       |}
//       |
//       |func (v *$structName) Set${renderMemberName(true)}(value time.Time) {
//       |    v.${renderMemberName(false)} = value
//       |}
//       |
//       |func (v *$structName) ${renderMemberName(true)}AsString() string {
//       |    year, month, day := v.${renderMemberName(false)}.Date()
//       |    return fmt.Sprintf("%04d:%02d:%02d", year, month, day)
//       |}
//       |
//       |func (v *$structName) Set${renderMemberName(true)}FromString(value string) error {
//       |    parts := strings.Split(value, ":")
//       |    if len(parts) != 3 {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the YYYY:MM:DD format. Got %s", value)
//       |    }
//       |
//       |    day, err := strconv.Atoi(parts[2])
//       |    if err != nil {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the YYYY:MM:DD format, day is invalid. Got %s", value)
//       |    }
//       |    month, err := strconv.Atoi(parts[1])
//       |    if err != nil {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the YYYY:MM:DD format, month is invalid. Got %s", value)
//       |    }
//       |    year, err := strconv.Atoi(parts[0])
//       |    if err != nil {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the YYYY:MM:DD format, year is invalid. Got %s", value)
//       |    }
//       |
//       |    v.${renderMemberName(false)} = time.Date(year, time.Month(month), day, 0, 0, 0, 0, time.UTC)
//       |    return nil
//       |}
//     """.stripMargin
//  }
//
//  def toTimeField(): String = {
//    s"""func (v *$structName) ${renderMemberName(true)}() time.Time {
//       |    return v.${renderMemberName(false)}
//       |}
//       |
//       |func (v *$structName) Set${renderMemberName(true)}(value time.Time) {
//       |    v.${renderMemberName(false)} = value
//       |}
//       |
//       |func (v *$structName) ${renderMemberName(true)}AsString() string {
//       |    hour := v.${renderMemberName(false)}.Hour()
//       |    minute := v.${renderMemberName(false)}.Minute()
//       |    second := v.${renderMemberName(false)}.Second()
//       |    millis := v.${renderMemberName(false)}.Nanosecond() / int((int64(time.Millisecond) / int64(time.Nanosecond)))
//       |    return fmt.Sprintf("%02d:%02d:%02d.%d", hour, minute, second, millis)
//       |}
//       |
//       |func (v *$structName) Set${renderMemberName(true)}FromString(value string) error {
//       |    parts := strings.Split(value, ":")
//       |    if len(parts) != 3 {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format. Got %s", value)
//       |    }
//       |    lastParts := strings.Split(parts[2], ".")
//       |    if len(lastParts) != 2 {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format, ms is missing. Got %s", value)
//       |    }
//       |
//       |    hour, err := strconv.Atoi(parts[0])
//       |    if err != nil {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format, hours are invalid. Got %s", value)
//       |    }
//       |    minute, err := strconv.Atoi(parts[1])
//       |    if err != nil {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format, minutes are invalid. Got %s", value)
//       |    }
//       |    second, err := strconv.Atoi(lastParts[0])
//       |    if err != nil {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format, seconds are invalid. Got %s", value)
//       |    }
//       |    millis, err := strconv.Atoi(lastParts[1])
//       |    if err != nil {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format, millis are invalid. Got %s", value)
//       |    }
//       |
//       |    v.${renderMemberName(false)} = time.Date(2000, time.January, 1, hour, minute, second, millis * int(time.Millisecond), time.UTC)
//       |    return nil
//       |}
//     """.stripMargin
//  }
//
//  def toTimeStampField(utc: Boolean): String = {
//    s"""func (v *$structName) ${renderMemberName(true)}() time.Time {
//       |    return v.${renderMemberName(false)}${if (utc) ".UTC()" else ""}
//       |}
//       |
//       |func (v *$structName) Set${renderMemberName(true)}(value time.Time) {
//       |    v.${renderMemberName(false)} = value
//       |}
//       |
//       |func (v *$structName) ${renderMemberName(true)}AsString() string {
//       |    layout := "2006-01-02T15:04:05.000000${if (utc) "Z" else "-07:00"}"
//       |    return v.${renderMemberName(false)}${if (utc) ".UTC()" else ""}.Format(layout)
//       |}
//       |
//       |func (v *$structName) Set${renderMemberName(true)}FromString(value string) error {
//       |    layout := "2006-01-02T15:04:05.000000${if (utc) "Z" else "-07:00"}"
//       |    t, err := time.Parse(layout, value)
//       |    if err != nil {
//       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the YYYY:MM:DDTHH:MM:SS.MICROS${if (utc) "Z" else "+00:00"} format. Got %s", value)
//       |    }
//       |
//       |    v.${renderMemberName(false)} = t
//       |    return nil
//       |}
//     """.stripMargin
//    //+01:00[Europe/Dublin]
//  }
//
//  def renderAssign(struct: String, variable: String, serialized: Boolean, optional: Boolean): String = {
//    renderAssignImpl(struct, tp.id, variable, serialized, optional)
//  }
//
//  private def renderDeserializedVar(id: TypeId, dest: String, src: String): String = id match {
//    case _: InterfaceId =>
//      s"""$dest, err := ${im.withImport(id)}Create${id.name}($src)
//         |if err != nil {
//         |    return err
//         |}
//           """.stripMargin
//
//    case _: AdtId =>
//      s"""$dest := &${im.withImport(id)}${id.name}{}
//         |if err := json.Unmarshal($src, $dest); err != nil {
//         |    return err
//         |}
//           """.stripMargin
//
//    case _: DTOId =>
//      s"""$dest := &${im.withImport(id)}${id.name}{}
//         |if err := $dest.LoadSerialized($src); err != nil {
//         |    return err
//         |}
//           """.stripMargin
//
//    case _: EnumId => // TODO Consider using automatic unmarshalling by placing in serialized structure just enum or identifier object
//      s"""if !${im.withImport(id)}IsValid${id.name}($src) {
//         |    return fmt.Errorf("Invalid ${id.name} enum value %s", $src)
//         |}
//         |$dest := ${im.withImport(id)}New${id.name}($src)
//           """.stripMargin
//
//    case _: IdentifierId => // TODO Consider using automatic unmarshalling by placing in serialized structure just enum or identifier object
//      s"""$dest := &${im.withImport(id)}${id.name}{}
//         |if err := $dest.LoadSerialized($src); err != nil {
//         |    return err
//         |}
//           """.stripMargin
//
//    case al: AliasId => renderDeserializedVar(ts(al).asInstanceOf[Alias].target, dest, src)
//
//    case _ => throw new IDLException("We should never get here for deserialized field.")
//  }
//
//  private def renderAssignImpl(struct: String, id: TypeId, variable: String, serialized: Boolean, optional: Boolean): String = {
//    if (serialized) {
//      val assignVar = if (optional || !tp.hasSetterError)
//        s"$struct.Set${renderMemberName(true)}($variable)"
//      else
//        s"""if err := $struct.Set${renderMemberName(true)}($variable); err != nil {
//           |    return err
//           |}
//         """.stripMargin
//
//      val tempVal = s"m${name.capitalize}"
//      val assignTemp = if (optional || !tp.hasSetterError)
//        s"$struct.Set${renderMemberName(true)}(${(if (optional) "&" else "") + tempVal})"
//      else
//        s"""if err := $struct.Set${renderMemberName(true)}($tempVal); err != nil {
//           |    return err
//           |}
//         """.stripMargin
//
//      id match {
//        case _: InterfaceId | _: AdtId | _: DTOId | _: EnumId | _: IdentifierId =>
//          s"""${renderDeserializedVar(id, tempVal, (if(optional) "*" else "") + variable)}
//             |$assignTemp
//           """.stripMargin
//
//        case al: AliasId => renderAssignImpl(struct, ts(al).asInstanceOf[Alias].target, variable, serialized, optional)
//        case g: Generic => g match {
//          case go: Generic.TOption =>
//            s"""if $variable != nil {
//               |${renderAssignImpl(struct, go.valueType, s"$variable", serialized, optional = true).shift(4)}
//               |}
//               """.stripMargin
//
//          // TODO This is probably not going to work well for nested generics
//          case _: Generic.TList | _: Generic.TSet => {
//            val vt = g match {
//              case gl: Generic.TList => gl.valueType
//              case gs: Generic.TSet => gs.valueType
//              case _ => throw new IDLException("Just preventing a warning here...")
//            }
//
//            if (GoLangType(null, im, ts).isPrimitive(vt))
//              assignVar
//            else
//              s"""$tempVal := make(${tp.renderType()}, len($variable))
//                 |for ${tempVal}Index, ${tempVal}Val := range $variable {
//                 |${if (vt.isInstanceOf[Primitive])
//                renderAssignImpl(struct, vt, s"${tempVal}Val", serialized, optional).shift(4) else
//                (renderDeserializedVar(vt, s"__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Index] = __dest").shift(4)}
//                 |}
//                 |$assignTemp
//                 """.stripMargin
//          }
//
//          case gm: Generic.TMap => {
//            val vt = gm.valueType
//
//            if (GoLangType(null, im, ts).isPrimitive(vt))
//              assignVar
//            else
//              s"""$tempVal := make(${tp.renderType()})
//                 |for ${tempVal}Key, ${tempVal}Val := range $variable {
//                 |${if (vt.isInstanceOf[Generic])
//                renderAssignImpl(struct, vt, s"${tempVal}Val", serialized, optional).shift(4) else
//                (renderDeserializedVar(vt, s"__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Key] = __dest").shift(4)}
//                 |}
//                 |$assignTemp
//                 """.stripMargin
//          }
//        }
//        case Primitive.TTsTz | Primitive.TTs | Primitive.TTime | Primitive.TDate =>
//          s"""if err := $struct.Set${renderMemberName(true)}FromString($variable); err != nil {
//             |    return err
//             |}
//           """.stripMargin
//
//        case _ => assignVar
//      }
//    } else {
//      val res = id match {
//        //        case Primitive.TTsTz | Primitive.TTs | Primitive.TTime | Primitive.TDate =>
//        //          s"$struct.Set${renderMemberName(true)}FromString($variable)"
//        case _ => s"$struct.Set${renderMemberName(true)}($variable)"
//      }
//      if (optional || !tp.hasSetterError)
//        res
//      else
//        s"if err := $res; err != nil {\n    return err\n}"
//    }
//  }
}

object CSharpField {
  def apply(
            field: Field,
            structName: String
          ) (implicit im: CSharpImports, ts: Typespace): CSharpField = new CSharpField(field.name, CSharpType(field.typeId), structName)
}
