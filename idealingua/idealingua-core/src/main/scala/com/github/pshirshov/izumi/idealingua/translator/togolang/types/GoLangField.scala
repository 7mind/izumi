package com.github.pshirshov.izumi.idealingua.translator.togolang.types

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

case class GoLangField(
                      name: String,
                      tp: GoLangType,
                      structName: String,
                      im: GoLangImports = GoLangImports(List.empty),
                      ts: Typespace = null
                      ) {
  def renderMemberName(capitalize: Boolean): String = {
    safeName(name, !capitalize)
  }

  protected def safeName(name: String, lower: Boolean = false): String = {
    val reserved = Seq("break", "default", "func", "interface", "select", "case", "defer", "go", "map", "struct",
      "chan", "else", "goto", "package", "switch", "const", "fallthrough", "if", "range", "type", "continue", "for",
      "import", "return", "var", "bool", "byte", "complex64", "complex128", "error", "float32", "float64", "int",
      "int8", "int16", "int32", "int64", "rune", "string", "uint", "uint16", "uint8", "uint32", "uint64", "uintptr",
      "true", "false", "iota", "nil", "append", "cap", "close", "complex", "copy", "delete", "imag", "len", "make",
      "new", "panic", "print", "println", "real", "recover", "time")

    val res = if (reserved.contains(name)) s"m${name.capitalize}" else name
    if (lower)
      Character.toLowerCase(res.charAt(0)) + res.substring(1)
    else
      res.capitalize
  }

  def renderInterfaceMethods(): String = {
    s"""${renderMemberName(true)}() ${tp.renderType()}
       |Set${renderMemberName(true)}(value ${tp.renderType()})${if (tp.hasSetterError()) " error" else ""}
     """.stripMargin
  }

  def renderMember(): String = {
    s"${renderMemberName(false)} ${tp.renderType()}"
  }

  def renderMethods(): String = tp.id match {
    case Primitive.TUUID => toGuidField()
    case Primitive.TTime => toTimeField()
    case Primitive.TDate => toDateField()
    case Primitive.TTs => toTimeStampField(false)
    case Primitive.TTsTz => toTimeStampField(true)
    case _ => toGenericField()
  }

  private def renderSetterNilCheck(): String = {
    s"""if value == nil {
       |    return fmt.Errorf("Set${renderMemberName(true)} is not optional")
       |}
     """.stripMargin
  }

  def toGenericField(): String = {
    s"""func (v *$structName) ${renderMemberName(true)}() ${tp.renderType()} {
       |    return v.${renderMemberName(false)}
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}(value ${tp.renderType()}) ${if (tp.hasSetterError()) "error " else ""}{
       |    ${if (tp.hasSetterError()) renderSetterNilCheck() else ""}
       |    v.${renderMemberName(false)} = value${if (tp.hasSetterError()) "\n    return nil" else ""}
       |}
     """.stripMargin
  }

  def toGuidField(): String = {
    s"""func (v *$structName) ${renderMemberName(true)}() string {
       |    return v.${renderMemberName(false)}
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}(value string) error {
       |    pattern := regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$$`)
       |    matched := pattern.MatchString(value)
       |    if !matched {
       |        return fmt.Errorf("Set${renderMemberName(true)} expects the value to be a valid UUID. Got %s", value)
       |    }
       |
       |    v.${renderMemberName(false)} = value
       |    return nil
       |}
     """.stripMargin
  }

  def toDateField(): String = {
    s"""func (v *$structName) ${renderMemberName(true)}() time.Time {
       |    return v.${renderMemberName(false)}
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}(value time.Time) {
       |    v.${renderMemberName(false)} = value
       |}
       |
       |func (v *$structName) ${renderMemberName(true)}AsString() string {
       |    year, month, day := v.${renderMemberName(false)}.Date()
       |    return fmt.Sprintf("%04d:%02d:%02d", year, month, day)
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}FromString(value string) error {
       |    parts := strings.Split(value, ":")
       |    if len(parts) != 3 {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the DD:MM:YYYY format. Got %s", value)
       |    }
       |
       |    day, err := strconv.Atoi(parts[0])
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the DD:MM:YYYY format, day is invalid. Got %s", value)
       |    }
       |    month, err := strconv.Atoi(parts[1])
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the DD:MM:YYYY format, month is invalid. Got %s", value)
       |    }
       |    year, err := strconv.Atoi(parts[2])
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the DD:MM:YYYY format, year is invalid. Got %s", value)
       |    }
       |
       |    v.${renderMemberName(false)} = time.Date(year, time.Month(month), day, 0, 0, 0, 0, time.UTC)
       |    return nil
       |}
     """.stripMargin
  }

  def toTimeField(): String = {
    s"""func (v *$structName) ${renderMemberName(true)}() time.Time {
       |    return v.${renderMemberName(false)}
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}(value time.Time) {
       |    v.${renderMemberName(false)} = value
       |}
       |
       |func (v *$structName) ${renderMemberName(true)}AsString() string {
       |    hour := v.${renderMemberName(false)}.Hour()
       |    minute := v.${renderMemberName(false)}.Minute()
       |    second := v.${renderMemberName(false)}.Second()
       |    millis := v.${renderMemberName(false)}.Nanosecond() / int((int64(time.Millisecond) / int64(time.Nanosecond)))
       |    return fmt.Sprintf("%02d:%02d:%02d.%d", hour, minute, second, millis)
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}FromString(value string) error {
       |    parts := strings.Split(value, ":")
       |    if len(parts) != 3 {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format. Got %s", value)
       |    }
       |    lastParts := strings.Split(parts[2], ".")
       |    if len(parts) != 2 {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format, ms is missing. Got %s", value)
       |    }
       |
       |    hour, err := strconv.Atoi(parts[0])
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format, hours are invalid. Got %s", value)
       |    }
       |    minute, err := strconv.Atoi(parts[1])
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format, minutes are invalid. Got %s", value)
       |    }
       |    second, err := strconv.Atoi(lastParts[0])
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format, seconds are invalid. Got %s", value)
       |    }
       |    millis, err := strconv.Atoi(lastParts[1])
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format, millis are invalid. Got %s", value)
       |    }
       |
       |    v.${renderMemberName(false)} = time.Date(2000, time.January, 1, hour, minute, second, millis * int(time.Millisecond), time.UTC)
       |    return nil
       |}
     """.stripMargin
  }

  def toTimeStampField(utc: Boolean): String = {
    s"""func (v *$structName) ${renderMemberName(true)}() time.Time {
       |    return v.${renderMemberName(false)}${if (utc) ".UTC()" else ""}
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}(value time.Time) {
       |    v.${renderMemberName(false)} = value
       |}
       |
       |func (v *$structName) ${renderMemberName(true)}AsString() string {
       |    layout := "2006-01-02T15:04:05.000000${if (utc) "Z[UTC]" else "-07:00[MST]"}"
       |    return v.${renderMemberName(false)}${if (utc) ".UTC()" else ""}.Format(layout)
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}FromString(value string) error {
       |    layout := "2006-01-02T15:04:05.000000${if (utc) "Z[UTC]" else "-07:00[MST]"}"
       |    t, err := time.Parse(layout, value)
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the YYYY:MM:DDTHH:MM:SS.MICROS${if (utc) "Z[UTC]" else "+00:00[Zone/Region]"} format. Got %s", value)
       |    }
       |
       |    v.${renderMemberName(false)} = t
       |    return nil
       |}
     """.stripMargin
    //+01:00[Europe/Dublin]
  }

  def renderAssign(struct: String, variable: String, serialized: Boolean, optional: Boolean): String = {
    renderAssignImpl(struct, tp.id, variable, serialized, optional)
  }

  private def renderDeserializedVar(id: TypeId, dest: String, src: String): String = id match {
    case _: InterfaceId =>
      s"""$dest, err := ${im.withImport(id)}Create${id.name}($src)
         |if err != nil {
         |    return err
         |}
           """.stripMargin

    case _: AdtId =>
      s"""$dest := &${im.withImport(id)}${id.name}{}
         |err = json.Unmarshal($src, $dest)
         |if err != nil {
         |    return err
         |}
           """.stripMargin

    case _: DTOId =>
      s"""$dest := &${im.withImport(id)}${id.name}{}
         |err = $dest.LoadSerialized($src)
         |if err != nil {
         |    return err
         |}
           """.stripMargin

    case _: EnumId => // TODO Consider using automatic unmarshalling by placing in serialized structure just enum or identifier object
      s"""if !${im.withImport(id)}IsValid${id.name}($src) {
         |    err = fmt.Errorf("Invalid ${id.name} enum value %s", $src)
         |}
         |if err != nil {
         |    return err
         |}
         |$dest := ${im.withImport(id)}New${id.name}($src)
           """.stripMargin

    case _: IdentifierId => // TODO Consider using automatic unmarshalling by placing in serialized structure just enum or identifier object
      s"""$dest := &${im.withImport(id)}${id.name}{}
         |err = $dest.LoadSerialized($src)
         |if err != nil {
         |    return err
         |}
           """.stripMargin

    case al: AliasId => renderDeserializedVar(ts(al).asInstanceOf[Alias].target, dest, src)

    case _ => throw new IDLException("We should never get here for deserialized field.")
  }

  private def renderAssignImpl(struct: String, id: TypeId, variable: String, serialized: Boolean, optional: Boolean): String = {
    if (serialized) {
      val assignVar = if (optional || !tp.hasSetterError())
        s"$struct.Set${renderMemberName(true)}($variable)"
      else
        s"""err = $struct.Set${renderMemberName(true)}($variable)
           |if err != nil {
           |    return err
           |}
         """.stripMargin

      val tempVal = s"m${name.capitalize}"
      val assignTemp = if (optional || !tp.hasSetterError())
        s"$struct.Set${renderMemberName(true)}(${(if (optional) "&" else "") + tempVal})"
      else
        s"""err = $struct.Set${renderMemberName(true)}($tempVal)
           |if err != nil {
           |    return err
           |}
         """.stripMargin

      id match {
        case _: InterfaceId | _: AdtId | _: DTOId | _: EnumId | _: IdentifierId =>
          s"""${renderDeserializedVar(id, tempVal, (if(optional) "*" else "") + variable)}
             |$assignTemp
           """.stripMargin

          case al: AliasId => renderAssignImpl(struct, ts(al).asInstanceOf[Alias].target, variable, serialized, optional)
          case g: Generic => g match {
            case go: Generic.TOption =>
              s"""if $variable != nil {
                 |${renderAssignImpl(struct, go.valueType, s"$variable", serialized, optional = true).shift(4)}
                 |}
               """.stripMargin

            // TODO This is probably not going to work well for nested generics
            case _: Generic.TList | _: Generic.TSet => {
              val vt = g match {
                case gl: Generic.TList => gl.valueType
                case gs: Generic.TSet => gs.valueType
                case _ => throw new IDLException("Just preventing a warning here...")
              }

              if (GoLangType(null, im, ts).isPrimitive(vt))
                assignVar
              else
                s"""$tempVal := make(${tp.renderType()}, len($variable))
                   |for ${tempVal}Index, ${tempVal}Val := range $variable {
                   |${if (vt.isInstanceOf[Primitive])
                  renderAssignImpl(struct, vt, s"${tempVal}Val", serialized, optional).shift(4) else
                  (renderDeserializedVar(vt, s"__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Index] = __dest").shift(4)}
                   |}
                   |$assignTemp
                 """.stripMargin
            }

            case gm: Generic.TMap => {
              val vt = gm.valueType

              if (GoLangType(null, im, ts).isPrimitive(vt))
                assignVar
              else
                s"""$tempVal := make(${tp.renderType()})
                   |for ${tempVal}Key, ${tempVal}Val := range $variable {
                   |${if (vt.isInstanceOf[Generic])
                      renderAssignImpl(struct, vt, s"${tempVal}Val", serialized, optional).shift(4) else
                      (renderDeserializedVar(vt, s"__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Key] = __dest").shift(4)}
                   |}
                   |$assignTemp
                 """.stripMargin
            }
          }
        case Primitive.TTsTz | Primitive.TTs | Primitive.TTime | Primitive.TDate =>
          s"""err = $struct.Set${renderMemberName(true)}FromString($variable)
             |if err != nil {
             |    return err
             |}
           """.stripMargin

        case _ => assignVar
      }
    } else {
      val res = id match {
//        case Primitive.TTsTz | Primitive.TTs | Primitive.TTime | Primitive.TDate =>
//          s"$struct.Set${renderMemberName(true)}FromString($variable)"
        case _ => s"$struct.Set${renderMemberName(true)}($variable)"
      }
      if (optional || !tp.hasSetterError())
        res
      else
        s"err := $res\nif err != nil {\n    return err\n}"
    }
  }
}

object GoLangField {
  def apply(
             name: String,
             tp: GoLangType,
             structName: String,
             im: GoLangImports = GoLangImports(List.empty),
             ts: Typespace = null
           ): GoLangField = new GoLangField(name, tp, structName, im, ts)
}
