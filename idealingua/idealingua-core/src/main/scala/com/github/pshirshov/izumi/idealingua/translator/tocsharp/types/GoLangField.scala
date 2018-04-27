package com.github.pshirshov.izumi.idealingua.translator.tocsharp.types

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

case class CSharpField(
                      name: String,
                      tp: CSharpType,
                      structName: String,
                      im: CSharpImports= CSharpImports(List.empty),
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
    case Primitive.TUUID => toGuidField
    case Primitive.TTime => toTimeField
    case Primitive.TDate => toDateField
    case Primitive.TTs => toDateField
    case Primitive.TTsTz => toDateField
    case _ => toGenericField
  }

  private def renderSetterNilCheck(): String = {
    s"""if value == nil {
       |    return fmt.Errorf("Set${renderMemberName(true)} is not optional")
       |}
     """.stripMargin
  }

  def toGenericField: String = {
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

  def toGuidField: String = {
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

  def toDateField: String = {
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
       |    return strconv.Itoa(day) + ":" + strconv.Itoa(int(month)) + ":" + strconv.Itoa(year)
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

  def toTimeField: String = {
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
       |    return strconv.Itoa(hour) + ":" + strconv.Itoa(minute) + ":" + strconv.Itoa(second) + "." + strconv.Itoa(millis)
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

  def renderAssign(struct: String, variable: String, serialized: Boolean, optional: Boolean): String = {
    renderAssignImpl(struct, tp.id, variable, serialized, optional)
  }

  private def renderAssignImpl(struct: String, id: TypeId, variable: String, serialized: Boolean, optional: Boolean = false): String = {
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
        s"$struct.Set${renderMemberName(true)}($tempVal)"
      else
        s"""err = $struct.Set${renderMemberName(true)}($tempVal)
           |if err != nil {
           |    return err
           |}
         """.stripMargin

      id match {
        case _: InterfaceId =>
          s"""$tempVal, err := ${im.withImport(id)}Create${id.name}($variable)
             |if err != nil {
             |    return err
             |}
             |$assignTemp
           """.stripMargin

        case _: AdtId =>
          s"""$tempVal := &${id.name}{}
             |err = json.Unmarshal($variable, $tempVal)
             |if err != nil {
             |    return err
             |}
             |$assignTemp
           """.stripMargin

        case _: DTOId =>
          s"""$tempVal := &${id.name}{}
             |err = $tempVal.LoadSerialized($variable)
             |if err != nil {
             |    return err
             |}
             |$assignTemp
           """.stripMargin

        case _: IdentifierId | _: EnumId => // TODO Consider using automatic unmarshalling by placing in serialized structure just enum or identifier object
          s"""$tempVal := &${id.name}{}
             |err = json.Unmarshal([]byte($variable), $tempVal)
             |if err != nil {
             |    return err
             |}
             |$assignTemp
           """.stripMargin

          case al: AliasId => renderAssignImpl(struct, ts(al).asInstanceOf[Alias].target, variable, serialized, optional)
          case g: Generic => g match {
            case go: Generic.TOption => renderAssignImpl(struct, go.valueType, variable, serialized, true)
            case gl: Generic.TList => "Not Implemented! Generic.TList in renderAssignImpl"
            case gm: Generic.TMap => "Not Implemented! Generic.TMap in renderAssignImpl"
            case gs: Generic.TSet => "Not Implemented! Generic.TSet in renderAssignImpl"
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
        case Primitive.TTsTz | Primitive.TTs | Primitive.TTime | Primitive.TDate =>
          s"$struct.Set${renderMemberName(true)}FromString($variable)"
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
             tp: CSharpType,
             structName: String,
             im: CSharpImports = CSharpImports(List.empty),
             ts: Typespace = null
           ): CSharpField = CSharpField(name, tp, structName, im, ts)
}
