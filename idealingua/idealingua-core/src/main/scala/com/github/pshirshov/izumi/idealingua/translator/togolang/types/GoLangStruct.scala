package com.github.pshirshov.izumi.idealingua.translator.togolang.types

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

final case class GoLangStruct(
                          name: String,
                          typeId: TypeId,
                          implements: List[InterfaceId] = List.empty,
                          fields: List[GoLangField] = List.empty,
                          imports: GoLangImports = GoLangImports(List.empty),
                          ts: Typespace = null,
                          ignoreSlices: List[InterfaceId] = List.empty
                       ) {
  def render(rtti: Boolean = true, makePrivate: Boolean = false, withTest: Boolean = true): String = {
    val name = if (makePrivate) (Character.toLowerCase(this.name.charAt(0)) + this.name.substring(1)) else this.name

    val test =
      s"""
         |func ${if (makePrivate) "newTest" else "NewTest"}$name() *$name {
         |    res := &$name{}
         |${fields.map(f => f.renderAssign("res", f.tp.testValue(), serialized = false, optional = true)).mkString("\n").shift(4)}
         |    return res
         |}
       """.stripMargin

    val res =
    s"""// $name structure
       |type $name struct {
       |${implements.map(intf => s"// Implements interface: ${intf.name}").mkString("\n").shift(4)}
       |${fields.map(f => f.renderMember()).mkString("\n").shift(4)}
       |}
       |
       |${fields.map(f => f.renderMethods()).filterNot(_.isEmpty).mkString("\n")}
       |
       |func ${if (makePrivate) "new" else "New"}$name(${fields.map(f => f.renderMemberName(false) + " " + f.tp.renderType()).mkString(", ")}) *$name {
       |    res := &$name{}
       |${fields.map(f => f.renderAssign("res", f.renderMemberName(false), serialized = false, optional = true)).mkString("\n").shift(4)}
       |    return res
       |}
       |${if (withTest) test else ""}
     """.stripMargin

    if (rtti) {
      renderRuntimeNames(consts = true) + "\n" + res + "\n" + renderRuntimeNames(methods = true)
    } else {
      res
    }
  }

  def renderSlices(): String = {
    val refined = implements.filterNot(ignoreSlices.contains)
    s"""${refined.map(intf => renderSlice(s"To${ts.implId(intf).name}Serialized", s"${ts.implId(intf).name}Serialized", getInterfaceFields(intf))).mkString("\n")}
       |${refined.map(intf => renderLoadSlice(s"Load${ts.implId(intf).name}Serialized", s"${ts.implId(intf).name}Serialized", getInterfaceFields(intf))).mkString("\n")}
     """.stripMargin
  }

  private def getInterfaceFields(iid: InterfaceId): List[GoLangField] = {
    val fields = ts.structure.structure(iid)
    fields.all.map(f => GoLangField(f.field.name, GoLangType(f.field.typeId, imports, ts), name, imports, ts))
  }

  private def renderRuntimeNames(consts: Boolean = false, methods: Boolean = false, holderName: String = null): String = {
    val pkg = typeId.path.toPackage.mkString(".")
    val constSection =
    s"""const (
       |    rtti${name}PackageName = "$pkg"
       |    rtti${name}ClassName = "$name"
       |    rtti${name}FullClassName = "$pkg.$name"
       |)
     """.stripMargin

    if (consts && !methods) {
      return constSection
    }

    val methodsSection =
    s"""// GetPackageName provides package where $name belongs
       |func (v *$name) GetPackageName() string {
       |    return rtti${if(holderName == null) name else holderName}PackageName
       |}
       |
       |// GetClassName provides short class name for RTTI purposes
       |func (v *$name) GetClassName() string {
       |    return rtti${if(holderName == null) name else holderName}ClassName
       |}
       |
       |// GetFullClassName provides full-qualified class name
       |func (v *$name) GetFullClassName() string {
       |    return rtti${if(holderName == null) name else holderName}FullClassName
       |}
     """.stripMargin

      if (!consts) {
        methodsSection
      } else {
        consts + "\n" + methodsSection
      }
  }

  private def isFieldPolymorph(f: GoLangField): Boolean = {
    return f.tp.isPolymorph(f.tp.id)
  }

  private def getRefinedFields(fields: List[GoLangField], polymorph: Boolean): List[GoLangField] = {
    if (polymorph) {
      fields.filter(isFieldPolymorph)
    } else {
      fields.filterNot(isFieldPolymorph)
    }
  }

  private def renderPolymorphSerialized(id: TypeId, dest: String, srcOriginal: String, forOption: Boolean = false): String = {
    val src =  if (forOption) s"(*$srcOriginal)" else srcOriginal

    id match {
      case _: InterfaceId =>
        s"""_$dest, err := json.Marshal($src)
           |if err != nil {
           |    return nil, err
           |}
           |$dest ${if(forOption) "" else ":"}= ${if(forOption) "&" else ""}map[string]json.RawMessage{v.GetFullClassName(): _$dest}
         """.stripMargin

      case _: AdtId =>
        s"""_$dest, err ${if(forOption) "" else ":"}= json.Marshal($src)
           |if err != nil {
           |    return nil, err
           |}
           |$dest := ${if(forOption) "&" else ""}_$dest
         """.stripMargin

      case _: IdentifierId | _: EnumId =>
        s"""_$dest := $src.String()
           |$dest ${if(forOption) "" else ":"}= ${if(forOption) "&" else ""}_$dest
       """.stripMargin

      case _: DTOId =>
        s"""_$dest, err ${if(forOption) "" else ":"}= $src.Serialize()
           |if err != nil {
           |    return nil, err
           |}
           |$dest := ${if(forOption) "&" else ""}_$dest
         """.stripMargin

      case _ => throw new IDLException("Should not get into renderPolymorphSerialized")
    }
  }

  private def renderPolymorphSerializedVar(id: TypeId, dest: String, src: String): String = {
    id match {
      case _: InterfaceId | _: AdtId | _: IdentifierId | _: DTOId | _: EnumId =>
        renderPolymorphSerialized(id, dest, src)

      case g: Generic => g match {
        case go: Generic.TOption =>
          s"""var $dest ${GoLangType(id, imports, ts).renderType(serialized = true)} = nil
             |if $src != nil {
             |${
                  if (GoLangType(id, imports, ts).isPolymorph(go.valueType))
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
          if (GoLangType(null, imports, ts).isPrimitive(vt))
            s"$dest := $src"
          else
            s"""$tempVal := make(${GoLangType(id, imports, ts).renderType(serialized = true)}, len($src))
               |for ${tempVal}Index, ${tempVal}Val := range $src {
               |${
                  if (vt.isInstanceOf[Generic])
                    renderPolymorphSerializedVar(vt, s"$tempVal[${tempVal}Index]", s"${tempVal}Val").shift(4) else
                    (renderPolymorphSerialized(vt, s"__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Index] = __dest").shift(4)
                }
               |}
               |$dest := $tempVal
                 """.stripMargin
        }

        case gm: Generic.TMap => {
          val vt = GoLangType(gm.valueType, imports, ts)
          val tempVal = s"_$dest"

          if (GoLangType(null, imports, ts).isPrimitive(vt.id))
            s"$dest := $src"
          else
            s"""$tempVal := make(${GoLangType(gm, imports, ts).renderType(serialized = true)})
               |for ${tempVal}Key, ${tempVal}Val := range $src {
               |${if (vt.isInstanceOf[Generic])
                  renderPolymorphSerializedVar(vt.id, s"$tempVal[${tempVal}Index]", s"${tempVal}Val").shift(4) else
                  (renderPolymorphSerialized(vt.id, s"__dest", s"${tempVal}Val") + s"\n$tempVal[${tempVal}Key] = __dest").shift(4)}
               |}
               |$dest := $tempVal
                 """.stripMargin
        }
      }

      case _ => throw new IDLException("Should never get into renderPolymorphSerializedVar with other types... " + id.name)
    }
  }

  private def renderPolymorphSerializedVar(f: GoLangField): String = {
      renderPolymorphSerializedVar(f.tp.id, s"ps${f.renderMemberName(true)}", s"v.${f.renderMemberName(false)}")
  }

  private def renderSerializedVar(f: GoLangField): String = {
    f.tp.id match {
      case Primitive.TTsTz => f.renderMemberName(true) + "AsString()"
      case Primitive.TTs => f.renderMemberName(true) + "AsString()"
      case Primitive.TDate => f.renderMemberName(true) + "AsString()"
      case Primitive.TTime => f.renderMemberName(true) + "AsString()"
      case _ => f.renderMemberName(false)
    }
  }

  def renderSlice(method: String, slice: String, fields: List[GoLangField]): String = {
    s"""func (v *$name) $method() (*$slice, error) {
       |${getRefinedFields(fields, polymorph = true).map(pf => renderPolymorphSerializedVar(pf)).mkString("\n").shift(4)}
       |    return &$slice{
       |${getRefinedFields(fields, polymorph = true).map(f => s"${f.renderMemberName(true)}: ps${f.renderMemberName(true)},").mkString("\n").shift(8)}
       |${getRefinedFields(fields, polymorph = false).map(f => s"${f.renderMemberName(true)}: v.${renderSerializedVar(f)},").mkString("\n").shift(8)}
       |    }, nil
       |}
     """.stripMargin
  }

  def renderLoadSlice(method: String, slice: String, fields: List[GoLangField]): String = {
    s"""func (v *$name) $method(slice *$slice) error {
       |    if slice == nil {
       |        return fmt.Errorf("method $method got nil input, expected a valid object")
       |    }
       |    ${if (!fields.exists(isFieldPolymorph)) "" else "var err error"}
       |${fields.map(f => f.renderAssign("v", s"slice.${f.renderMemberName(true)}", serialized = true, optional = false)).mkString("\n").shift(4)}
       |    return nil
       |}
     """.stripMargin
  }

  def renderSerialized(makePrivate: Boolean = false): String = {
    val name = if (makePrivate) Character.toLowerCase(this.name.charAt(0)) + this.name.substring(1) else this.name

    s"""type ${name}Serialized struct {
       |${fields.map(f => s"${f.renderMemberName(true)} ${f.tp.renderType(true)} `json:" + "\"" + f.name + (if (f.tp.id.isInstanceOf[Generic.TOption]) ",omitempty" else "") + "\"`").mkString("\n").shift(4)}
       |}
       |
       |${renderSlice("Serialize", s"${name}Serialized", fields)}
       |${renderLoadSlice("LoadSerialized", s"${name}Serialized", fields)}
       |
       |func (v *$name) MarshalJSON() ([]byte, error) {
       |    serialized, err := v.Serialize()
       |    if err != nil {
       |        return nil, err
       |    }
       |    return json.Marshal(serialized)
       |}
       |
       |func (v *$name) UnmarshalJSON(data []byte) error {
       |    serialized := &${name}Serialized{}
       |    if err := json.Unmarshal(data, serialized); err != nil {
       |        return err
       |    }
       |
       |    return v.LoadSerialized(serialized)
       |}
     """.stripMargin
  }
}

object GoLangStruct {
  def apply(
             name: String,
             typeId: TypeId,
             implements: List[InterfaceId] = List.empty,
             fields: List[GoLangField] = List.empty,
             imports: GoLangImports = GoLangImports(List.empty),
             ts: Typespace = null,
             ignoreSlices: List[InterfaceId] = List.empty
           ): GoLangStruct = new GoLangStruct(name, typeId, implements, fields, imports, ts, ignoreSlices)
}
