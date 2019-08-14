package izumi.idealingua.translator.togolang.types

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common.{Generic, TypeId}
import izumi.idealingua.model.typespace.Typespace

final case class GoLangStruct(
                          name: String,
                          typeId: TypeId,
                          implements: List[InterfaceId] = List.empty,
                          fields: List[GoLangField] = List.empty,
                          imports: GoLangImports,
                          ts: Typespace = null,
                          ignoreSlices: List[InterfaceId] = List.empty
                       ) {
  def render(rtti: Boolean = true, withTest: Boolean = true): String = {
    val test =
      s"""
         |func NewTest$name() *$name {
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
       |func New$name(${fields.map(f => f.renderMemberName(false) + " " + f.tp.renderType()).mkString(", ")}) *$name {
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
    s"""${refined.map(intf => renderSlice(s"To${intf.name + ts.tools.implId(intf).name}Serialized", s"${imports.withImport(intf) + intf.name + ts.tools.implId(intf).name}Serialized", getInterfaceFields(intf))).mkString("\n")}
       |${refined.map(intf => renderLoadSlice(s"Load${intf.name + ts.tools.implId(intf).name}Serialized", s"${imports.withImport(intf) + intf.name + ts.tools.implId(intf).name}Serialized", getInterfaceFields(intf))).mkString("\n")}
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
       |    rtti${name}ClassName = "${typeId.name}"
       |    rtti${name}FullClassName = "${typeId.wireId}"
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
    f.tp.isPolymorph(f.tp.id)
  }

  private def getRefinedFields(fields: List[GoLangField], polymorph: Boolean): List[GoLangField] = {
    if (polymorph) {
      fields.filter(isFieldPolymorph)
    } else {
      fields.filterNot(isFieldPolymorph)
    }
  }

  def renderSlice(method: String, slice: String, fields: List[GoLangField]): String = {
    s"""func (v *$name) $method() (*$slice, error) {
       |${getRefinedFields(fields, polymorph = true).map(pf => pf.renderPolymorphSerializedVar(s"ps${pf.renderMemberName(true)}", s"v.${pf.renderMemberName(false)}")).mkString("\n").shift(4)}
       |    return &$slice{
       |${getRefinedFields(fields, polymorph = true).map(f => s"${f.renderMemberName(true)}: ps${f.renderMemberName(true)},").mkString("\n").shift(8)}
       |${getRefinedFields(fields, polymorph = false).map(f => s"${f.renderMemberName(true)}: v.${f.renderSerializedVar()},").mkString("\n").shift(8)}
       |    }, nil
       |}
     """.stripMargin
  }

  def renderLoadSlice(method: String, slice: String, fields: List[GoLangField]): String = {
    s"""func (v *$name) $method(slice *$slice) error {
       |    if slice == nil {
       |        return fmt.Errorf("method $method got nil input, expected a valid object")
       |    }
       |${fields.map(f => f.renderAssign("v", s"slice.${f.renderMemberName(true)}", serialized = true, optional = false)).mkString("\n").shift(4)}
       |    return nil
       |}
     """.stripMargin
  }

  def renderSerialized(): String = {
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
             imports: GoLangImports,
             ts: Typespace = null,
             ignoreSlices: List[InterfaceId] = List.empty
           ): GoLangStruct = new GoLangStruct(name, typeId, implements, fields, imports, ts, ignoreSlices)
}
