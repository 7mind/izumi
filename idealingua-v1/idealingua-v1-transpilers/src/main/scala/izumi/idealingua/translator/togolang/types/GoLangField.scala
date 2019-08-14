package izumi.idealingua.translator.togolang.types

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import izumi.idealingua.model.typespace.Typespace

final case class GoLangField(
                      name: String,
                      tp: GoLangType,
                      structName: String,
                      im: GoLangImports,
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

  private def renderPolymorphSerialized(id: TypeId, dest: String, srcOriginal: String, forOption: Boolean = false): String = {
    val src =  if (forOption) s"(*$srcOriginal)" else srcOriginal

    id match {
      case Primitive.TTime =>
        if (forOption)
          s"""_$dest := irt.WriteTime($src)
             |$dest = &_$dest
           """.stripMargin
        else
          s"""$dest := irt.WriteTime($src)""".stripMargin

      case Primitive.TDate =>
        if (forOption)
          s"""_$dest := irt.WriteDate($src)
             |$dest = &_$dest
         """.stripMargin
        else
          s"""$dest := irt.WriteDate($src)""".stripMargin

      case Primitive.TTsU =>
        if (forOption)
          s"""_$dest := irt.WriteUTCDateTime($src)
             |$dest = &_$dest
         """.stripMargin
        else
          s"""$dest := irt.WriteUTCDateTime($src)""".stripMargin

      case Primitive.TTs =>
        if (forOption)
          s"""_$dest := irt.WriteLocalDateTime($src)
             |$dest = &_$dest
           """.stripMargin
        else
          s"""$dest := irt.WriteLocalDateTime($src)""".stripMargin

      case Primitive.TTsTz =>
        if (forOption)
          s"""_$dest := irt.WriteZoneDateTime($src)
             |$dest = &_$dest
           """.stripMargin
        else
          s"""$dest := irt.WriteZoneDateTime($src)""".stripMargin

      case _: InterfaceId =>
        s"""_$dest, err := json.Marshal($src)
           |if err != nil {
           |    return nil, err
           |}
           |$dest ${if(forOption) "" else ":"}= ${if(forOption) "&" else ""}map[string]json.RawMessage{$src.GetFullClassName(): _$dest}
         """.stripMargin

      case _: AdtId =>
        s"""_$dest, err := json.Marshal($src)
           |if err != nil {
           |    return nil, err
           |}
           |$dest ${if(forOption) "" else ":"}= ${if(forOption) s"interface{}(&_$dest).(*json.RawMessage) // A bit hacky, but the underlying type for json.RawMessage is []byte, so should be legit" else s"_$dest"}
         """.stripMargin

      case _: IdentifierId | _: EnumId =>
        s"""_$dest := $src.String()
           |$dest ${if(forOption) "" else ":"}= ${if(forOption) "&" else ""}_$dest
       """.stripMargin

      case _: DTOId =>
        s"""_$dest, err := $src.Serialize()
           |if err != nil {
           |    return nil, err
           |}
           |$dest ${if(forOption) "" else ":"}= ${if(forOption) "&" else ""}_$dest
         """.stripMargin

      case al: AliasId => renderPolymorphSerialized(ts.dealias(al), dest, srcOriginal, forOption)

      case _ => throw new IDLException(s"Should not get into renderPolymorphSerialized ${id.name} dest: $dest src: $srcOriginal")
    }
  }


  private def renderPolymorphSerializedVar(id: TypeId, dest: String, src: String): String = {
    id match {
      case _: AliasId => renderPolymorphSerializedVar(ts.dealias(id), dest, src)

      case _: InterfaceId | _: AdtId | _: IdentifierId | _: DTOId | _: EnumId =>
        renderPolymorphSerialized(id, dest, src)

      case Primitive.TDate | Primitive.TTime | Primitive.TTsTz | Primitive.TTs | Primitive.TTsU =>
          renderPolymorphSerialized(id, dest, src)

      case g: Generic => g match {
        case go: Generic.TOption =>
          s"""var $dest ${GoLangType(id, im, ts).renderType(serialized = true)} = nil
             |if $src != nil {
             |${
            if (GoLangType(id, im, ts).isPolymorph(go.valueType))
              renderPolymorphSerialized(go.valueType, dest, src, forOption = true).shift(4)
            else
              s"    $dest = $src"}
             |}
           """.stripMargin

        case _: Generic.TList | _: Generic.TSet => {
          val vt = g match {
            case gl: Generic.TList => gl.valueType
            case gs: Generic.TSet => gs.valueType
            case _ => throw new IDLException("Just preventing a warning here...")
          }

          val tempVal = s"_$dest"

          // TODO This is not going to work well for nested lists
          if (!GoLangType(null, im, ts).isPolymorph(vt))
            s"$dest := $src"
          else
            s"""$tempVal := make(${GoLangType(id, im, ts).renderType(serialized = true)}, len($src))
               |for ${tempVal}Index, ${tempVal}Val := range $src {
               |${
              if (vt.isInstanceOf[Generic])
                renderPolymorphSerializedVar(vt, s"$tempVal[${tempVal}Index]", s"${tempVal}Val").shift(4) else
                (renderPolymorphSerialized(vt, "__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Index] = __dest").shift(4)
            }
               |}
               |$dest := $tempVal
                 """.stripMargin
        }

        case gm: Generic.TMap => {
          val vt = GoLangType(gm.valueType, im, ts)
          val tempVal = s"_$dest"


          if (!GoLangType(null, im, ts).isPolymorph(vt.id))
            s"$dest := $src"
          else
          // TODO: there was this thing in the for loop below
          //
          //if (vt.isInstanceOf[Generic])
          //              renderPolymorphSerializedVar(vt.id, s"$tempVal[${tempVal}Index]", s"${tempVal}Val").shift(4) else
          // but vt can't be a generic

            s"""$tempVal := make(${GoLangType(gm, im, ts).renderType(serialized = true)})
               |for ${tempVal}Key, ${tempVal}Val := range $src {
               |  ${(renderPolymorphSerialized(vt.id, "__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Key] = __dest").shift(4)}
               |}
               |$dest := $tempVal
                 """.stripMargin
        }
      }

      case _ => throw new IDLException("Should never get into renderPolymorphSerializedVar with other types... " + id.name + " " + id.getClass().getName())
    }
  }

  def renderPolymorphSerializedVar(dest: String, src: String): String = {
    renderPolymorphSerializedVar(tp.id, dest, src)
  }

  def renderSerializedVar(): String = {
    tp.id match {
      case Primitive.TTsTz => renderMemberName(true) + "AsString()"
      case Primitive.TTs => renderMemberName(true) + "AsString()"
      case Primitive.TTsU => renderMemberName(true) + "AsString()"
      case Primitive.TDate => renderMemberName(true) + "AsString()"
      case Primitive.TTime => renderMemberName(true) + "AsString()"
      case g: Generic => g match {
        case go: Generic.TOption => go.valueType match {
          case Primitive.TTsTz | Primitive.TTs | Primitive.TTsU | Primitive.TDate | Primitive.TTime => renderMemberName(true) + "AsString()"
          case _ => renderMemberName(false)
        }
        case _ => renderMemberName(false)
      }
      case _ => renderMemberName(false)
    }
  }

  def renderInterfaceMethods(): String = {
    s"""${renderMemberName(true)}() ${tp.renderType()}
       |Set${renderMemberName(true)}(value ${tp.renderType()})${if (tp.hasSetterError) " error" else ""}
     """.stripMargin
  }

  def renderMember(): String = {
    s"${renderMemberName(false)} ${tp.renderType()}"
  }

  def renderMethods(): String = tp.id match {
    case Primitive.TUUID => toGuidField()
    case Primitive.TBLOB => ???
    case Primitive.TTime => toTimeField()
    case Primitive.TDate => toDateField()
    case Primitive.TTs => toTimeStampField(local = true)
    case Primitive.TTsTz => toTimeStampField(local = false)
    case Primitive.TTsU => toTimeStampField(local = false, utc = true)
    case g: Generic => g match {
      case go: Generic.TOption => go.valueType match {
        case Primitive.TTime => toTimeField(optional = true)
        case Primitive.TDate => toDateField(optional = true)
        case Primitive.TTs => toTimeStampField(local = true, optional = true)
        case Primitive.TTsTz => toTimeStampField(local = false, optional = true)
        case Primitive.TTsU => toTimeStampField(local = false, optional = true, utc = true)
        case _ => toGenericField()
      }
      case _ => toGenericField()
    }
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
       |func (v *$structName) Set${renderMemberName(true)}(value ${tp.renderType()}) ${if (tp.hasSetterError) "error " else ""}{
       |    ${if (tp.hasSetterError) renderSetterNilCheck() else ""}
       |    v.${renderMemberName(false)} = value${if (tp.hasSetterError) "\n    return nil" else ""}
       |}
     """.stripMargin
  }

  def toGuidField(): String = {
    s"""func (v *$structName) ${renderMemberName(true)}() string {
       |    return v.${renderMemberName(false)}
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}(value string) error {
       |    pattern := regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$$`)
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

  def toDateField(optional: Boolean = false): String = {
    s"""func (v *$structName) ${renderMemberName(true)}() ${if (optional) "*" else ""}time.Time {
       |    return v.${renderMemberName(false)}
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}(value ${if (optional) "*" else ""}time.Time) {
       |    v.${renderMemberName(false)} = value
       |}
       |
       |func (v *$structName) ${renderMemberName(true)}AsString() ${if (optional) "*" else ""}string {
       |    ${if (optional) s"if v.${renderMemberName(false)} == nil {\n        return nil\n    }" else ""}
       |    res := irt.WriteDate(${if (optional) "*" else ""}v.${renderMemberName(false)})
       |    return ${if (optional) "&" else ""}res
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}FromString(value string) error {
       |    res, err := irt.ReadDate(value)
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the YYYY-MM-DD format. Got %s", value)
       |    }
       |    v.${renderMemberName(false)} = ${if (optional) "&" else ""}res
       |    return nil
       |}
     """.stripMargin
  }

  def toTimeField(optional: Boolean = false): String = {
    s"""func (v *$structName) ${renderMemberName(true)}() ${if (optional) "*" else ""}time.Time {
       |    return v.${renderMemberName(false)}
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}(value ${if (optional) "*" else ""}time.Time) {
       |    v.${renderMemberName(false)} = value
       |}
       |
       |func (v *$structName) ${renderMemberName(true)}AsString() ${if (optional) "*" else ""}string {
       |    ${if (optional) s"if v.${renderMemberName(false)} == nil {\n        return nil\n    }" else ""}
       |    res := irt.WriteTime(${if (optional) "*" else ""}v.${renderMemberName(false)})
       |    return ${if (optional) "&" else ""}res;
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}FromString(value string) error {
       |    res, err := irt.ReadTime(value)
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the HH:MM:SS.MS format. Got %s", value)
       |    }
       |
       |    v.${renderMemberName(false)} = ${if (optional) "&" else ""}res
       |    return nil
       |}
     """.stripMargin
  }

  def toTimeStampField(local: Boolean, optional: Boolean = false, utc: Boolean = false): String = {
    s"""func (v *$structName) ${renderMemberName(true)}() ${if (optional) "*" else ""}time.Time {
       |    return v.${renderMemberName(false)}
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}(value ${if (optional) "*" else ""}time.Time) {
       |    v.${renderMemberName(false)} = value
       |}
       |
       |func (v *$structName) ${renderMemberName(true)}AsString() ${if (optional) "*" else ""}string {
       |    ${if (optional) s"if v.${renderMemberName(false)} == nil {\n        return nil\n    }" else ""}
       |    res := irt.Write${if(local)"Local" else if(utc) "UTC" else "Zone"}DateTime(${if (optional) "*" else ""}v.${renderMemberName(false)})
       |    return ${if (optional) "&" else ""}res
       |}
       |
       |func (v *$structName) Set${renderMemberName(true)}FromString(value string) error {
       |    t, err := irt.Read${if(local)"Local" else if(utc) "UTC" else "Zone"}DateTime(value)
       |    if err != nil {
       |        return fmt.Errorf("Set${renderMemberName(true)} value must be in the YYYY-MM-DDTHH:MM:SS.MIC${if (local) "" else "+00:00"} format. Got %s", value)
       |    }

       |    v.${renderMemberName(false)} = ${if (optional) "&" else ""}t
       |    return nil
       |}
     """.stripMargin
  }

  def renderAssign(struct: String, variable: String, serialized: Boolean, optional: Boolean): String = {
    renderAssignImpl(struct, tp.id, variable, serialized, optional)
  }

  private def renderDeserializedVar(refId: TypeId, dest: String, src: String, usageId: Option[TypeId] = None): String = {
    val id = if (usageId.isDefined) usageId.get else refId
    refId match {
      case Primitive.TTime =>
        s"""$dest, err := irt.ReadTime($src)
           |if err != nil {
           |    return err
           |}
         """.stripMargin

      case Primitive.TDate =>
        s"""$dest, err := irt.ReadDate($src)
           |if err != nil {
           |    return err
           |}
         """.stripMargin

      case Primitive.TTsU =>
        s"""$dest, err := irt.ReadUTCDateTime($src)
           |if err != nil {
           |    return err
           |}
         """.stripMargin

      case Primitive.TTs =>
        s"""$dest, err := irt.ReadLocalDateTime($src)
           |if err != nil {
           |    return err
           |}
         """.stripMargin

      case Primitive.TTsTz =>
        s"""$dest, err := irt.ReadZoneDateTime($src)
           |if err != nil {
           |    return err
           |}
         """.stripMargin

      case _: InterfaceId =>
        s"""$dest, err := ${im.withImport(id)}Create${id.name}($src)
           |if err != nil {
           |    return err
           |}
           """.stripMargin

      case _: AdtId =>
        s"""$dest := &${im.withImport(id)}${id.name}{}
           |if err := json.Unmarshal($src, $dest); err != nil {
           |    return err
           |}
           """.stripMargin

      case _: DTOId =>
        s"""$dest := &${im.withImport(id)}${id.name}{}
           |if err := $dest.LoadSerialized($src); err != nil {
           |    return err
           |}
           """.stripMargin

      case _: EnumId => // TODO Consider using automatic unmarshalling by placing in serialized structure just enum or identifier object
        s"""if !${im.withImport(id)}IsValid${id.name}($src) {
           |    return fmt.Errorf("Invalid ${id.name} enum value %s", $src)
           |}
           |$dest := ${im.withImport(id)}New${id.name}($src)
           """.stripMargin

      case _: IdentifierId => // TODO Consider using automatic unmarshalling by placing in serialized structure just enum or identifier object
        s"""$dest := &${im.withImport(id)}${id.name}{}
           |if err := $dest.LoadSerialized($src); err != nil {
           |    return err
           |}
           """.stripMargin

      case al: AliasId => renderDeserializedVar(ts.dealias(al), dest, src, Some(al))

      case _ => throw new IDLException("We should never get here for deserialized field.")
    }
  }

  private def renderAssignImpl(struct: String, id: TypeId, variable: String, serialized: Boolean, optional: Boolean): String = {
    if (serialized) {
      val assignVar = if (optional || !tp.hasSetterError)
        s"$struct.Set${renderMemberName(true)}($variable)"
      else
        s"""if err := $struct.Set${renderMemberName(true)}($variable); err != nil {
           |    return err
           |}
         """.stripMargin

      val tempVal = s"m${name.capitalize}"
      val assignTemp = if (optional || !tp.hasSetterError)
        s"$struct.Set${renderMemberName(true)}(${(if (optional) "&" else "") + tempVal})"
      else
        s"""if err := $struct.Set${renderMemberName(true)}($tempVal); err != nil {
           |    return err
           |}
         """.stripMargin

      id match {
        case _: InterfaceId | _: AdtId | _: DTOId | _: EnumId | _: IdentifierId =>
          s"""${renderDeserializedVar(id, tempVal, (if(optional) "*" else "") + variable)}
             |$assignTemp
           """.stripMargin

          case al: AliasId => ts.dealias(al) match {
            case _: InterfaceId | _: AdtId | _: DTOId | _: EnumId | _: IdentifierId =>
              s"""${renderDeserializedVar(ts.dealias(al), tempVal, (if(optional) "*" else "") + variable, Some(al))}
                 |$assignTemp
               """.stripMargin

            case _ => renderAssignImpl(struct, ts(al).asInstanceOf[Alias].target, variable, serialized, optional)
          }
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

              if (!GoLangType(null, im, ts).isPolymorph(vt))
                assignVar
              else
                s"""$tempVal := make(${tp.renderType()}, len($variable))
                   |for ${tempVal}Index, ${tempVal}Val := range $variable {
                   |${if (vt.isInstanceOf[Primitive])
                  renderAssignImpl(struct, vt, s"${tempVal}Val", serialized, optional).shift(4) else
                  (renderDeserializedVar(vt, "__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Index] = __dest").shift(4)}
                   |}
                   |$assignTemp
                 """.stripMargin
            }

            case gm: Generic.TMap => {
              val vt = gm.valueType

              if (!GoLangType(null, im, ts).isPolymorph(vt))
                assignVar
              else
                s"""$tempVal := make(${tp.renderType()})
                   |for ${tempVal}Key, ${tempVal}Val := range $variable {
                   |${if (vt.isInstanceOf[Generic])
                      renderAssignImpl(struct, vt, s"${tempVal}Val", serialized, optional).shift(4) else
                      (renderDeserializedVar(vt, "__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Key] = __dest").shift(4)}
                   |}
                   |$assignTemp
                 """.stripMargin
            }
          }
        case Primitive.TTsTz | Primitive.TTs | Primitive.TTsU | Primitive.TTime | Primitive.TDate =>
          s"""if err := $struct.Set${renderMemberName(true)}FromString(${if (optional) "*" else ""}$variable); err != nil {
             |    return err
             |}
           """.stripMargin

        case _ => assignVar
      }
    } else {
      val res = id match {
//        case Primitive.TTsTz | Primitive.TTs | Primitive.TTsU | Primitive.TTime | Primitive.TDate =>
//          s"$struct.Set${renderMemberName(true)}FromString($variable)"
        case _ => s"$struct.Set${renderMemberName(true)}($variable)"
      }
      if (optional || !tp.hasSetterError)
        res
      else
        s"if err := $res; err != nil {\n    return err\n}"
    }
  }
}

object GoLangField {
  def apply(
             name: String,
             tp: GoLangType,
             structName: String,
             im: GoLangImports,
             ts: Typespace = null
           ): GoLangField = new GoLangField(name, tp, structName, im, ts)
}
