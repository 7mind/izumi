package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod.Output.{Algebraic, Singular, Struct, Void, Alternative}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.GoLangBuildManifest
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.Translator
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler.GoTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.togolang.products.CogenProduct._
import com.github.pshirshov.izumi.idealingua.translator.togolang.products.RenderableCogenProduct
import com.github.pshirshov.izumi.idealingua.translator.togolang.types._

object GoLangTranslator {
  final val defaultExtensions = Seq(
  )
}

class GoLangTranslator(ts: Typespace, options: GoTranslatorOptions) extends Translator {
  protected val ctx: GLTContext = new GLTContext(ts, options.extensions)

  import ctx._

  def translate(): Seq[Module] = {
    val modules = Seq(
      typespace.domain.types.flatMap(translateDef)
      , typespace.domain.services.flatMap(translateService)
      , typespace.domain.emitters.flatMap(translateEmitter)
    ).flatten

    val extendedModules = addRuntime(options, modules)
    if (options.manifest.isDefined && options.manifest.get.useRepositoryFolders)
      extendedModules.map(m => Module(ModuleId(options.manifest.get.repository.split("/") ++ m.id.path, m.id.name), m.content))
    else
      extendedModules
  }


  protected def translateService(definition: Service): Seq[Module] = {
    ctx.modules.toSource(definition.id.domain, ctx.modules.toModuleId(definition.id),
      ctx.modules.toTestModuleId(definition.id), renderService(definition))
  }

  protected def translateEmitter(definition: Emitter): Seq[Module] = {
    ctx.modules.toSource(definition.id.domain, ctx.modules.toModuleId(definition.id),
      ctx.modules.toTestModuleId(definition.id), renderEmitter(definition))
  }

  protected def translateDef(definition: TypeDef): Seq[Module] = {
    val defns = definition match {
      case i: Alias =>
        renderAlias(i)
      case i: Enumeration =>
        renderEnumeration(i)
      case i: Identifier =>
        renderIdentifier(i)
      case i: Interface =>
        renderInterface(i)
      case d: DTO =>
        renderDto(d)
      case d: Adt =>
        renderAdt(d)
      case _ =>
        RenderableCogenProduct.empty
    }

    ctx.modules.toSource(definition.id.path.domain, ctx.modules.toModuleId(definition),
      ctx.modules.toTestModuleId(definition), defns)
  }

  protected def renderRegistrationCtor(interface: InterfaceId, structName: String, imports: GoLangImports): String = {
    s"""func ctor${structName}For${interface.name}() ${imports.withImport(interface)}${interface.name} {
       |    return &$structName{}
       |}
     """.stripMargin
  }

  protected def renderRegistrations(interfaces: Interfaces, structName: String, imports: GoLangImports): String = {
    if (interfaces.isEmpty) {
      return ""
    }

    val uniqueInterfaces = interfaces.groupBy(_.name).map(_._2.head)

    s"""${uniqueInterfaces.map(sc => renderRegistrationCtor(sc, structName, imports)).mkString("\n")}
       |
       |func init() {
       |    // Here we register current DTO in other interfaces
       |${uniqueInterfaces.map(sc => s"${imports.withImport(sc)}Register${sc.name}(rtti${structName}FullClassName, ctor${structName}For${sc.name})").mkString("\n").shift(4)}
       |}
     """.stripMargin
  }

  protected def renderDto(i: DTO): RenderableCogenProduct = {
    val imports = GoLangImports(i, i.id.path.toPackage, ts, manifest = options.manifest)

    val fields = typespace.structure.structure(i).all.map(f => if (f.defn.variance.nonEmpty) f.defn.variance.last else f.field )
    val distinctFields = fields.groupBy(_.name).map(_._2.head)

    val struct = GoLangStruct(
      i.id.name,
      i.id,
      i.struct.superclasses.interfaces,
      distinctFields.map(df => GoLangField(df.name, GoLangType(df.typeId, imports, ts), i.id.name, imports, ts)).toList,
      imports,
      ts
    )

    val dto =
      s"""${struct.render()}
         |${struct.renderSerialized()}
         |${struct.renderSlices()}
         |${renderRegistrations(ts.inheritance.parentsInherited(i.id), i.id.name, imports)}
       """.stripMargin

    val testImports = GoLangImports(struct.fields.flatMap(f => if (f.tp.testValue() != "nil")
      GoLangImports.collectTypes(f.tp.id) else List.empty), i.id.path.toPackage, ts, List.empty, forTest = true, manifest = options.manifest)

    val tests =
      s"""${testImports.renderImports(Seq("testing", "encoding/json"))}
         |
         |func Test${i.id.name}JSONSerialization(t *testing.T) {
         |    v1 := New${i.id.name}(${struct.fields.map(f => GoLangType(f.tp.id, testImports, ts).testValue()).mkString(", ")})
         |    serialized, err := json.Marshal(v1)
         |    if err != nil {
         |        t.Fatalf("Type '%s' should serialize into JSON using Marshal. %s", "${i.id.name}", err.Error())
         |    }
         |    var v2 ${i.id.name}
         |    err = json.Unmarshal(serialized, &v2)
         |    if err != nil {
         |        t.Fatalf("Type '%s' should deserialize from JSON using Unmarshal. %s", "${i.id.name}", err.Error())
         |    }
         |    serialized2, err := json.Marshal(&v2)
         |    if err != nil {
         |        t.Fatalf("Type '%s' should serialize into JSON using Marshal. %s", "${i.id.name}", err.Error())
         |    }
         |
         |    if string(serialized) != string(serialized2) {
         |        t.Errorf("type '%s' serialization to JSON and from it afterwards must return the same value. Got '%s' and '%s'", "${i.id.name}", string(serialized), string(serialized2))
         |    }
         |}
       """.stripMargin

    CompositeProduct(dto, imports.renderImports(List("encoding/json", "fmt")), tests)

  }

  protected def renderAlias(i: Alias): RenderableCogenProduct = {
      val imports = GoLangImports(i, i.id.path.toPackage, ts, manifest = options.manifest)
      val goType = GoLangType(i.target, imports, ts)
      var extra: Seq[String] = Seq.empty

      val aliases = ts.dealias(i.id) match {
        case _: DTOId =>
          s"""type ${i.id.name}Serialized = ${goType.renderType(forAlias = true)}Serialized
           """.stripMargin
        case ii: InterfaceId =>
          extra = Seq("encoding/json")
          s"""type ${i.id.name + ts.tools.implId(ii).name} = ${goType.renderType(forAlias = true)}Struct
             |type ${i.id.name + ts.tools.implId(ii).name}Serialized = ${goType.renderType(forAlias = true)}${ts.tools.implId(ii).name}Serialized
             |func Create${i.id.name}(data map[string]json.RawMessage) (${i.id.name}, error) {
             |    return ${imports.withImport(i.target)}Create${i.target.name}(data)
             |}
           """.stripMargin
        case _: EnumId =>
          s"""func New${i.id.name}(e string) ${i.id.name} {
             |    return ${imports.withImport(i.target)}New${i.target.name}(e)
             |}
             |
             |func IsValid${i.id.name}(e string) bool {
             |    return ${imports.withImport(i.target)}IsValid${i.target.name}(e)
             |}
             |
             |func GetAll${i.id.name}() []${i.id.name} {
             |    return ${imports.withImport(i.target)}GetAll${i.target.name}()
             |}
           """.stripMargin
        case _ => ""
      }

      AliasProduct(
        s"""// ${i.id.name} alias
           |type ${i.id.name} = ${goType.renderType(forAlias = true)}
           |$aliases
           |func NewTest${i.id.name}() ${goType.renderType()} {
           |    return ${goType.testValue()}
           |}
         """.stripMargin,
        imports.renderImports(extra)
      )
  }

  protected def renderAdtMember(structName: String, member: AdtMember, im: GoLangImports): String = {
    val serializationName =  member.name
    val typeName = GoLangType(member.typeId, im, ts).renderType()

    s"""func (v *$structName) Is$serializationName() bool {
       |    return v.valueType == "$serializationName"
       |}
       |
       |func (v *$structName) Get$serializationName() $typeName {
       |    if !v.Is$serializationName() {
       |        return nil
       |    }
       |
       |    obj, ok := v.value.($typeName)
       |    if !ok {
       |        return nil
       |    }
       |
       |    return obj
       |}
       |
       |func (v *$structName) Set$serializationName(obj $typeName) {
       |    v.value = obj
       |    v.valueType = "$serializationName"
       |}
       |
       |func New${structName}From${member.typeId.name}(v $typeName) *$structName {
       |    res := &$structName{}
       |    res.Set$serializationName(v)
       |    return res
       |}
     """.stripMargin
  }

  protected def renderAdtSerialization(member: AdtMember, imports: GoLangImports): String = {
    val glt = GoLangType(member.typeId, imports, ts)
    val glf = GoLangField("value", glt, "v", imports, ts)
    if (glt.isPolymorph(glt.id)) {
      s"""polyVar, ok := v.value.(${glt.renderType()})
         |if !ok {
         |    return nil, fmt.Errorf("Out of sync stored object type %s and actual object.", v.valueType)
         |}
         |
         |${glf.renderPolymorphSerializedVar("polymorphRaw", "polyVar")}
         |serialized, err := json.Marshal(polymorphRaw)
         |if err != nil {
         |    return nil, err
         |}
         |
         |return json.Marshal(&map[string]json.RawMessage {
         |    v.valueType: serialized,
         |})
       """.stripMargin
    } else {
      """serialized, err := json.Marshal(v.value)
         |if err != nil {
         |    return nil, err
         |}
         |
         |return json.Marshal(&map[string]json.RawMessage {
         |    v.valueType: serialized,
         |})
       """.stripMargin
    }
  }

  protected def renderAdtImpl(name: String, alternatives: List[AdtMember], imports: GoLangImports, withTest: Boolean = true): String = {
      val test =
        s"""
           |func NewTest$name() *$name {
           |    res := &$name{}
           |    res.Set${alternatives.head.name}(NewTest${alternatives.head.typeId.name + (alternatives.head.typeId match { case iface: InterfaceId => ts.tools.implId(iface).name; case _ => "" })}())
           |    return res
           |}
         """.stripMargin

      // TODO Rebuild with type switcher
//    func typeSwitch(tst interface{}) {
//      switch v := tst.(type) {
//        case Stringer:
//        fmt.Println("Stringer:", v)
//        default:
//          fmt.Println("Unknown")
//      }
//    }

      s"""type $name struct {
         |    value interface{}
         |    // valueType could be removed and optimized using .(type) or reflect package assertion,
         |    // however there would be a problem with interface types inside of the ADT, they would reflect
         |    // to the original structure, making it impossible to detect whether something is
         |    // an interface (for unknown at compile time types, but provided as some implementations
         |    // known only to the app code).
         |    valueType string
         |}
         |
         |${alternatives.map(al => renderAdtMember(name, al, imports)).mkString("\n")}
         |
         |${if (withTest) test else ""}
         |
         |func (v *$name) MarshalJSON() ([]byte, error) {
         |    if v.value == nil {
         |        return nil, fmt.Errorf("trying to serialize a non-initialized Adt $name")
         |    }
         |
         |    switch v.valueType {
         |${alternatives.map(al => "case \"" + al.name + "\": {\n" + renderAdtSerialization(al, imports).shift(4) + "\n}").mkString("\n").shift(8)}
         |        default:
         |            return nil, fmt.Errorf("$name encountered an unknown type '%s' during serialization", v.valueType)
         |    }
         |}
         |
         |func (v *$name) UnmarshalJSON(data []byte) error {
         |    raw := map[string]json.RawMessage{}
         |    if err := json.Unmarshal(data, &raw); err != nil {
         |        return err
         |    }
         |
         |    for className, content := range raw {
         |        v.valueType = className
         |        switch className {
         |${alternatives.map(al => "case \"" + al.name + "\": {\n" + GoLangType(al.typeId, imports, ts).renderUnmarshal("content", "v.value = ").shift(4) + "\n    return nil\n}").mkString("\n").shift(12)}
         |            default:
         |                return fmt.Errorf("$name encountered an unknown type '%s' during deserialization", className)
         |        }
         |    }
         |
         |    return fmt.Errorf("$name expects a root key to be present, empty object found")
         |}
       """.stripMargin
  }

  protected def renderAdtAlternativeTest(i: Adt, m: AdtMember, im: GoLangImports): String = {
    s"""func Test${i.id.name}As${m.name}(t *testing.T) {
       |    adt := &${i.id.name}{}
       |    adt.Set${m.name}(${GoLangType(m.typeId, im, ts).testValue()})
       |
       |    if !adt.Is${m.name}() {
       |        t.Errorf("type '%s' Is${m.name} must be true.", "${i.id.name}")
       |    }
       |
       |${i.alternatives.map(al => if (al.name == m.name) "" else s"""if adt.Is${al.name}() {\n    t.Errorf("type '%s' Is${al.name} must be false.", "${i.id.name}")\n}""").mkString("\n").shift(4)}
       |
       |    serialized, err := json.Marshal(adt)
       |    if err != nil {
       |        t.Errorf("type '%s' json serialization failed. %+v", "${i.id.name}", err)
       |    }
       |
       |    adt2 := &${i.id.name}{}
       |    err = json.Unmarshal(serialized, adt2)
       |    if err != nil {
       |        t.Errorf("type '%s' json deserialization failed. %+v, json: %s", "${i.id.name}", err, string(serialized))
       |    }
       |
       |    if !adt2.Is${m.name}() {
       |        t.Errorf("type '%s' Is${m.name} must be true after deserialization.", "${i.id.name}")
       |    }
       |}
     """.stripMargin
  }

  protected def renderAdt(i: Adt): RenderableCogenProduct = {
    val imports = GoLangImports(i, i.id.path.toPackage, ts, manifest = options.manifest)
    val name = i.id.name

    val tests =
      s"""import (
         |    "testing"
         |    "encoding/json"
         |)
         |
         |${i.alternatives.map(al => renderAdtAlternativeTest(i, al, imports)).mkString("\n")}
       """.stripMargin

    AdtProduct(renderAdtImpl(name, i.alternatives, imports), imports.renderImports(Seq("fmt", "encoding/json")), tests)
  }

  protected def renderEnumeration(i: Enumeration): RenderableCogenProduct = {
    val name = i.id.name
    val decl =
    s"""// $name Enumeration
       |type $name ${if (i.members.length <= 255) "uint8" else "uint16"}
       |
       |const (
       |${i.members.map(m => s"// $m enum value\n" + (if (m == i.members.head) s"${name + m} $name = iota" else (name + m))).mkString("\n").shift(4)}
       |)
       |
       |var map${name}ToString = map[$name]string{
       |${i.members.map(m => s"${name + m}: " + "\"" + m + "\",").mkString("\n").shift(4)}
       |}
       |
       |var allOf$name = []$name{
       |${i.members.map(m => name + m + ",").mkString("\n").shift(4)}
       |}
       |
       |var mapStringTo$name = map[string]$name{
       |${i.members.map(m => "\"" + m + "\": " + s"${name + m},").mkString("\n").shift(4)}
       |}
       |
       |// String converts an enum to a string
       |func (e $name) String() string {
       |    return map${name}ToString[e]
       |}
       |
       |// New$name creates a new enum from string
       |func New$name(e string) $name {
       |    return mapStringTo$name[e]
       |}
       |
       |func GetAll$name() []$name {
       |    return allOf$name
       |}
       |
       |func NewTest$name() $name {
       |    return mapStringTo$name["${i.members.head}"]
       |}
       |
       |// IsValid$name checks if the string value can be converted to an enum
       |func IsValid$name(e string) bool {
       |    _, ok := mapStringTo$name[e]
       |    return ok
       |}
       |
       |// MarshalJSON deserialization for the enumeration
       |func (e $name) MarshalJSON() ([]byte, error) {
       |    // Enums are encoded into a string
       |    buffer := bytes.NewBufferString(`"`)
       |    buffer.WriteString(e.String())
       |    buffer.WriteString(`"`)
       |    return buffer.Bytes(), nil
       |}
       |
       |// UnmarshalJSON deserialization for the enumeration
       |func (e *$name) UnmarshalJSON(b []byte) error {
       |    // Restore enum from a string
       |    var s string
       |    err := json.Unmarshal(b, &s)
       |    if err != nil {
       |        return err
       |    }
       |
       |    *e = New$name(s)
       |    return nil
       |}
     """.stripMargin

    val tests =
      s"""import (
         |    "testing"
         |    "encoding/json"
         |)
         |
         |func Test${name}Creation(t *testing.T) {
         |    if IsValid${name}("${i.members.head}Invalid") {
         |        t.Errorf("type '%s' IsValid function should correctly identify invalid string enums", "$name")
         |    }
         |
         |    if !IsValid${name}("${i.members.head}") {
         |        t.Errorf("type '%s' IsValid function should correctly identify valid string enums", "$name")
         |    }
         |
         |    v1 := New${name}("${i.members.head}")
         |    if !IsValid${name}(v1.String()) {
         |        t.Errorf("type '%s' should be possible to create via New method with '%s' value", "$name", "${name + i.members.head}")
         |    }
         |
         |    v2 := ${name + i.members.head}
         |    if v1 != v2 {
         |        t.Errorf("type '%s' created from enum const and a corresponding string must return the same value. Got '%+v' and '%+v'", "$name", v1, v2)
         |    }
         |}
         |
         |func Test${name}StringSerialization(t *testing.T) {
         |    v1 := New${name}("${i.members.head}")
         |    v2 := New${name}(v1.String())
         |    if v1 != v2 {
         |        t.Errorf("type '%s' to string and new from that string must return the same enum value. Got '%+v' and '%+v'", "$name", v1, v2)
         |    }
         |}
         |
         |func Test${name}JSONSerialization(t *testing.T) {
         |    v1 := New${name}("${i.members.head}")
         |    serialized, err := json.Marshal(v1)
         |    if err != nil {
         |        t.Fatalf("type '%s' should serialize into JSON using Marshal. %s", "$name", err.Error())
         |    }
         |    var v2 $name
         |    err = json.Unmarshal(serialized, &v2)
         |    if err != nil {
         |        t.Fatalf("Type '%s' should deserialize from JSON using Unmarshal. %s", "$name", err.Error())
         |    }
         |
         |    if v1 != v2 {
         |        t.Errorf("type '%s' serialization to JSON and from it afterwards must return the same enum value. Got '%+v' and '%+v'", "$name", v1, v2)
         |    }
         |}
       """.stripMargin

    EnumProduct(
      decl,
      GoLangImports(List.empty, options.manifest).renderImports(Seq("encoding/json", "bytes")),
      tests
    )
  }

  protected def renderIdentifier(i: Identifier): RenderableCogenProduct = {
    val imports = GoLangImports(i, i.id.path.toPackage, ts, manifest = options.manifest)

    val fields = typespace.structure.structure(i)
    val sortedFields = fields.all.sortBy(_.field.name)

    val struct = GoLangStruct(
      i.id.name,
      i.id,
      List.empty,
      sortedFields.map(sf => GoLangField(sf.field.name, GoLangType(sf.field.typeId, imports, ts), i.id.name, imports, ts)),
      imports,
      ts
    )

    val needsStrconv = struct.fields.exists(f => f.tp.id match {
      case Primitive.TInt8 => true
      case Primitive.TInt16 => true
      case Primitive.TInt32 => true
      case Primitive.TInt64 => true

      case Primitive.TUInt8 => true
      case Primitive.TUInt16 => true
      case Primitive.TUInt32 => true
      case Primitive.TUInt64 => true
      case _ => false
    })

    val decl =
      s"""${struct.render()}
         |
         |// String converts an identifier to a string
         |func (v *${i.id.name}) String() string {
         |    suffix := ${sortedFields.map(f => "url.QueryEscape(" + GoLangType(f.field.typeId).renderToString(s"v.${f.field.name}") + ")").mkString(" + \":\" + ")}
         |    return "${i.id.name}#" + suffix
         |}
         |
         |func (v *${i.id.name}) Serialize() string {
         |    return v.String()
         |}
         |
         |func (v *${i.id.name}) LoadSerialized(s string) error {
         |    if !strings.HasPrefix(s, "${i.id.name}#") {
         |        return fmt.Errorf("expected identifier for type ${i.id.name}, got %s", s)
         |    }
         |
         |    parts := strings.Split(s[${i.id.name.length + 1}:], ":")
         |    if len(parts) != ${struct.fields.length} {
         |        return fmt.Errorf("expected identifier for type ${i.id.name} with ${struct.fields.length} parts, got %d in string %s", len(parts), s)
         |    }
         |
         |${struct.fields.zipWithIndex.map { case (f, index) => f.tp.renderFromString(f.renderMemberName(false), s"parts[$index]", unescape = true) }.mkString("\n").shift(4)}
         |
         |${struct.fields.map(f => f.renderAssign("v", f.renderMemberName(false), serialized = false, optional = false)).mkString("\n").shift(4)}
         |    return nil
         |}
         |
         |// MarshalJSON serialization for the identifier
         |func (v *${i.id.name}) MarshalJSON() ([]byte, error) {
         |    buffer := bytes.NewBufferString(`"`)
         |    buffer.WriteString(v.String())
         |    buffer.WriteString(`"`)
         |    return buffer.Bytes(), nil
         |}
         |
         |// UnmarshalJSON deserialization for the identifier
         |func (v *${i.id.name}) UnmarshalJSON(b []byte) error {
         |    var s string
         |    err := json.Unmarshal(b, &s)
         |    if err != nil {
         |        return err
         |    }
         |
         |    return v.LoadSerialized(s)
         |}
       """.stripMargin

    val testImports = struct.fields.flatMap(f => f.tp.testValuePackage()).distinct

    val tests =
      s"""import (
         |    "testing"
         |    "encoding/json"
         |${testImports.map(fi => "\"" + fi + "\"") .mkString("\n").shift(4)}
         |)
         |
         |func Test${i.id.name}Creation(t *testing.T) {
         |    v := New${i.id.name}(${struct.fields.map(f => f.tp.testValue()).mkString(", ")})
         |    if v == nil {
         |        t.Errorf("identifier of type ${i.id.name} should be possible to create with New method")
         |    }
         |}
         |
         |func Test${i.id.name}JSONSerialization(t *testing.T) {
         |    v1 := New${i.id.name}(${struct.fields.map(f => f.tp.testValue()).mkString(", ")})
         |    serialized, err := json.Marshal(v1)
         |    if err != nil {
         |        t.Fatalf("Type '%s' should serialize into JSON using Marshal. %s", "${i.id.name}", err.Error())
         |    }
         |    var v2 ${i.id.name}
         |    err = json.Unmarshal(serialized, &v2)
         |    if err != nil {
         |        t.Fatalf("Type '%s' should deserialize from JSON using Unmarshal. %s", "${i.id.name}", err.Error())
         |    }
         |
         |    if v1.String() != v2.String() {
         |        t.Errorf("type '%s' serialization to JSON and from it afterwards must return the same identifier value. Got '%s' and '%s'", "${i.id.name}", v1.String(), v2.String())
         |    }
         |}
       """.stripMargin

    IdentifierProduct(decl, imports.renderImports(
        if (needsStrconv)
          List("encoding/json", "bytes", "net/url", "fmt", "strings", "strconv")
        else
          List("encoding/json", "bytes", "net/url", "strings", "fmt")
      ),
      tests
    )
  }

  protected def renderInterface(i: Interface): RenderableCogenProduct = {
    val imports = GoLangImports(i, i.id.path.toPackage, ts, manifest = options.manifest)

    val fields = typespace.structure.structure(i).all.map(f => if (f.defn.variance.nonEmpty) f.defn.variance.last else f.field )
    val distinctFields = fields.groupBy(_.name).map(_._2.head)

    val implId = typespace.tools.implId(i.id)
    val eid = i.id.name + typespace.tools.implId(i.id).name

    val struct = GoLangStruct(
      eid,
      implId,
      i.struct.superclasses.interfaces ++ List(i.id),
      distinctFields.map(df => GoLangField(df.name, GoLangType(df.typeId, imports, ts), eid, imports, ts)).toList,
      imports,
      ts,
      List(i.id)
    )

    val iface =
      s"""type ${i.id.name} interface {
         |${i.struct.superclasses.interfaces.map(ifc => s"// implements ${ifc.name} interface").mkString("\n").shift(4)}
         |${struct.fields.map(f => f.renderInterfaceMethods()).mkString("\n").shift(4)}
         |    GetPackageName() string
         |    GetClassName() string
         |    GetFullClassName() string
         |}
       """.stripMargin

    val companion =
      s"""${struct.render()}
         |${struct.renderSerialized()}
         |${struct.renderSlices()}
         |
         |// Polymorphic section below. If a new type to be registered, use Register${i.id.name} method
         |// which will add it to the known list. You can also overwrite the existing registrations
         |// in order to provide extended functionality on existing models, preserving the original class name.
         |
         |type ${i.id.name}Constructor func() ${i.id.name}
         |
         |func ctor${eid}() ${i.id.name} {
         |    return &${eid}{}
         |}
         |
         |var known${i.id.name}Polymorphic = map[string]${i.id.name}Constructor {
         |    rtti${eid}FullClassName: ctor${eid},
         |}
         |
         |// Register${i.id.name} registers a new constructor for a polymorphic type ${i.id.name}
         |func Register${i.id.name}(className string, ctor ${i.id.name}Constructor) {
         |    known${i.id.name}Polymorphic[className] = ctor
         |}
         |
         |// Create${i.id.name} creates an instance of type ${i.id.name} in a polymorphic way
         |func Create${i.id.name}(data map[string]json.RawMessage) (${i.id.name}, error) {
         |    for className, content := range data {
         |        ctor, ok := known${i.id.name}Polymorphic[className]
         |        if !ok {
         |            return nil, fmt.Errorf("unknown polymorphic type '%s' for Create${i.id.name}", className)
         |        }
         |
         |        instance := ctor()
         |        err := json.Unmarshal(content, instance)
         |        if err != nil {
         |            return nil, err
         |        }
         |
         |        return instance, nil
         |    }
         |
         |    return nil, fmt.Errorf("empty content for polymorphic type in Create${i.id.name}")
         |}
         |${renderRegistrations(ts.inheritance.parentsInherited(i.id), eid, imports)}
       """.stripMargin

    val testImports = GoLangImports(struct.fields.flatMap(f => if (f.tp.testValue() != "nil")
      GoLangImports.collectTypes(f.tp.id) else List.empty), i.id.path.toPackage, ts, List.empty, forTest = true, manifest = options.manifest)

    val tests =
      s"""${testImports.renderImports(Seq("testing", "encoding/json"))}
         |
         |func Test${i.id.name}Creation(t *testing.T) {
         |    v := New${i.id.name + ts.tools.implId(i.id).name}(${struct.fields.map(f => f.tp.testValue()).mkString(", ")})
         |    if v == nil {
         |        t.Errorf("interface of type ${i.id.name} should be possible to create with New method.")
         |    }
         |}
         |
         |func Test${i.id.name}JSONSerialization(t *testing.T) {
         |    v1 := New${i.id.name + ts.tools.implId(i.id).name}(${struct.fields.map(f => f.tp.testValue()).mkString(", ")})
         |    serialized, err := json.Marshal(v1)
         |    if err != nil {
         |        t.Fatalf("Type '%s' should serialize into JSON using Marshal. %s", "${i.id.name}", err.Error())
         |    }
         |    var v2 ${i.id.name + ts.tools.implId(i.id).name}
         |    err = json.Unmarshal(serialized, &v2)
         |    if err != nil {
         |        t.Fatalf("Type '%s' should deserialize from JSON using Unmarshal. %s", "${i.id.name}", err.Error())
         |    }
         |    serialized2, err := json.Marshal(&v2)
         |    if err != nil {
         |        t.Fatalf("Type '%s' should serialize into JSON using Marshal. %s", "${i.id.name}", err.Error())
         |    }
         |
         |    if string(serialized) != string(serialized2) {
         |        t.Errorf("type '%s' serialization to JSON and from it afterwards must return the same value. Got '%s' and '%s'", "${i.id.name}", string(serialized), string(serialized2))
         |    }
         |}
       """.stripMargin

    InterfaceProduct(iface, companion, imports.renderImports(List("encoding/json", "fmt")), tests)
  }

  protected def inName(svcOrEmitter: String, name: String, public: Boolean): String = {
    if (public)
      s"In${svcOrEmitter.capitalize}${name.capitalize}"
    else
      s"in${svcOrEmitter.capitalize}${name.capitalize}"
  }

  protected def outName(svcOrEmitter: String, name: String, public: Boolean): String = {
    if (public)
      s"Out${svcOrEmitter.capitalize}${name.capitalize}"
    else
      s"out${svcOrEmitter.capitalize}${name.capitalize}"
  }

  protected def renderRPCMethodSignature(svcOrEmitter: String, method: DefMethod, imports: GoLangImports, spread: Boolean = false, withContext: Boolean = false): String = {
    method match {
      case m: DefMethod.RPCMethod => {
        val context = if (withContext) s"context interface{}${if (m.signature.input.fields.isEmpty) "" else ", "}" else ""
        if (spread) {
          val fields = m.signature.input.fields.map(f => GoLangField(f.name, GoLangType(f.typeId, imports, ts), "").renderMemberName(capitalize = false) + " " + GoLangType(f.typeId, imports, ts).renderType()).mkString(", ")
          s"${m.name.capitalize}($context$fields) ${renderRPCMethodOutputSignature(svcOrEmitter, m, imports)}"
        } else {
          s"${m.name.capitalize}(${context}input: ${inName(svcOrEmitter, m.name, public = true)}) ${renderRPCMethodOutputSignature(svcOrEmitter, m, imports)}"
        }
      }
    }
  }

  protected def isServiceMethodOutputNullable(method: DefMethod.RPCMethod): Boolean = method.signature.output match {
    case _: Struct => true
    case _: Algebraic => true
    case _: Singular => false // Should better detect here, there might be a singular object returned, which is nullable GoLangType(si.typeId, imports, ts).isPrimitive(si.typeId)
    case _: Void => false
    case _: Alternative => true
  }

  protected def isServiceMethodOutputExistent(method: DefMethod.RPCMethod): Boolean = method.signature.output match {
    case _: Void => false
    case _ => true
  }

  protected def renderServiceMethodOutputModel(svcOrEmitter: String, method: DefMethod.RPCMethod, imports: GoLangImports): String = method.signature.output match {
    case _: Struct => s"*${outName(svcOrEmitter, method.name, public = true)}"
    case _: Algebraic => s"*${outName(svcOrEmitter, method.name, public = true)}"
    case si: Singular => s"${GoLangType(si.typeId, imports, ts).renderType()}"
    case _: Void => ""
    case _: Alternative => throw new Exception("Not implemented")
  }

  protected def renderRPCMethodOutputSignature(svcOrEmitter: String, method: DefMethod.RPCMethod, imports: GoLangImports): String = {
    if (isServiceMethodOutputExistent(method))
      s"(${renderServiceMethodOutputModel(svcOrEmitter, method, imports)}, error)"
    else
      s"error"
  }

  protected def renderRPCClientMethod(svcOrEmitter: String, method: DefMethod, imports: GoLangImports): String = method match {
    case m: DefMethod.RPCMethod => m.signature.output match {
      case _: Struct | _: Algebraic =>
        s"""func (c *${svcOrEmitter}Client) ${renderRPCMethodSignature(svcOrEmitter, method, imports, spread = true)} {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"inData := New${inName(svcOrEmitter, m.name, public = true)}(${m.signature.input.fields.map(ff => GoLangField(ff.name, GoLangType(ff.typeId, imports, ts), "").renderMemberName(capitalize = false)).mkString(", ")})" }
           |    outData := &${outName(svcOrEmitter, m.name, public = true)}{}
           |    err := c.transport.Send("${svcOrEmitter}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "nil" else "inData"}, outData)
           |    if err != nil {
           |        return ${renderServiceMethodDefaultResult(method, imports)}, err
           |    }
           |    return outData, nil
           |}
       """.stripMargin

      case so: Singular =>
        val resType = GoLangType(so.typeId, imports, ts)
        s"""func (c *${svcOrEmitter}Client) ${renderRPCMethodSignature(svcOrEmitter, method, imports, spread = true)} {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"inData := New${inName(svcOrEmitter, m.name, public = true)}(${m.signature.input.fields.map(ff => GoLangField(ff.name, GoLangType(ff.typeId, imports, ts), "").renderMemberName(capitalize = false)).mkString(", ")})" }
           |    ${if (resType.isPrimitive(so.typeId)) s"var outData ${resType.renderType(forAlias = true)}" else s"outData := &${resType.renderType(forAlias = true)}{}"}
           |    err := c.transport.Send("${svcOrEmitter}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "nil" else "inData"}, ${if (resType.isPrimitive(so.typeId)) "&" else ""}outData)
           |    if err != nil {
           |        return ${renderServiceMethodDefaultResult(method, imports)}, err
           |    }
           |    return outData, nil
           |}
       """.stripMargin

      case _: Void =>
        s"""func (c *${svcOrEmitter}Client) ${renderRPCMethodSignature(svcOrEmitter, method, imports, spread = true)} {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"inData := New${inName(svcOrEmitter, m.name, public = true)}(${m.signature.input.fields.map(ff => GoLangField(ff.name, GoLangType(ff.typeId, imports, ts), "").renderMemberName(capitalize = false)).mkString(", ")})" }
           |    return c.transport.Send("${svcOrEmitter}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "nil" else "inData"}, nil)
           |}
       """.stripMargin

      case _: Alternative => throw new Exception("Alternative is not implemented.")
    }
  }

  protected def renderServiceClient(i: Service, imports: GoLangImports): String = {
    val name = s"${i.id.name}Client"

    s"""type ${i.id.name} interface {
       |${i.methods.map(m => renderRPCMethodSignature(i.id.name, m, imports, spread = true)).mkString("\n").shift(4)}
       |}
       |
       |type $name struct {
       |    ${i.id.name}
       |    transport irt.ClientTransport
       |}
       |
       |func (v *$name) SetTransport(t irt.ClientTransport) error {
       |    if t == nil {
       |        return fmt.Errorf("method SetTransport requires a valid transport, got nil")
       |    }
       |
       |    v.transport = t
       |    return nil
       |}
       |
       |func (v *$name) SetHTTPTransport(endpoint string, timeout time.Duration) {
       |    v.transport = irt.NewHTTPClientTransport(endpoint, timeout)
       |}
       |
       |func (v *$name) SetWebSocketTransport(endpoint string, subprotocols []string) error {
       |    transport, err := irt.NewWebSocketClientTransport(endpoint, subprotocols)
       |    if err != nil {
       |        return err
       |    }
       |
       |    v.transport = transport
       |    return nil
       |}
       |
       |func New${name}OverWebSocket(endpoint string, subprotocols []string) (*$name, error) {
       |    res := &$name{}
       |    err := res.SetWebSocketTransport(endpoint, subprotocols)
       |    if err != nil {
       |        return nil, err
       |    }
       |    return res, nil
       |}
       |
       |func New${name}OverHTTP(endpoint string, timeout time.Duration) *$name{
       |    res := &$name{}
       |    res.SetHTTPTransport(endpoint, timeout)
       |    return res
       |}
       |
       |${i.methods.map(me => renderRPCClientMethod(i.id.name, me, imports)).mkString("\n")}
     """.stripMargin
  }

  protected def renderDispatcherHandler(svcOrEmitter: String, method: DefMethod, service: String): String = method match {
    case m: DefMethod.RPCMethod =>
      if (isServiceMethodOutputExistent(m)) {
        s"""case "${m.name}": {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"modelIn := &${inName(svcOrEmitter, m.name, public = true)}{}\n    if err := v.marshaller.Unmarshal(data, modelIn); err != nil {\n        return nil, fmt.Errorf(" + "\"invalid input data object for method " + m.name + ":\" + err.Error())\n    }"}
           |    modelOut, err := v.$service.${m.name.capitalize}(context${if(m.signature.input.fields.isEmpty) "" else ", "}${m.signature.input.fields.map(f => s"modelIn.${GoLangField(f.name, GoLangType(f.typeId), "").renderMemberName(capitalize = true)}()").mkString(", ")})
           |    if err != nil {
           |        return []byte{}, err
           |    }
           |
           |    ${if (isServiceMethodOutputNullable(m)) s"""if modelOut == nil {\n        return []byte{}, fmt.Errorf("Method ${m.name} returned neither error nor result. Implementation might be broken.")\n    }""" else ""}
           |    dataOut, err := v.marshaller.Marshal(modelOut)
           |    if err != nil {
           |        return []byte{}, fmt.Errorf("Marshalling model failed: %s", err.Error())
           |    }
           |
           |    return dataOut, nil
           |}
           |
       """.stripMargin
      } else {
        s"""case "${m.name}": {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"modelIn := &${inName(svcOrEmitter, m.name, public = true)}{}\n    if err := v.marshaller.Unmarshal(data, modelIn); err != nil {\n        return nil, fmt.Errorf(" + "\"invalid input data object for method " + m.name + ":\" + err.Error())\n    }"}
           |    return []byte{}, v.$service.${m.name.capitalize}(context${if(m.signature.input.fields.isEmpty) "" else ", "}${m.signature.input.fields.map(f => s"modelIn.${GoLangField(f.name, GoLangType(f.typeId), "").renderMemberName(capitalize = true)}()").mkString(", ")})
           |}
           |
       """.stripMargin
      }

  }

  protected def renderServiceDispatcher(i: Service, imports: GoLangImports): String = {
    val name = s"${i.id.name}Dispatcher"

    s"""type ${i.id.name}Server interface {
       |${i.methods.map(m => renderRPCMethodSignature(i.id.name, m, imports, spread = true, withContext = true)).mkString("\n").shift(4)}
       |}
       |
       |type $name struct {
       |    server ${i.id.name}Server
       |    marshaller irt.Marshaller
       |}
       |
       |func (v *$name) SetServer(s ${i.id.name}Server) error {
       |    if s == nil {
       |        return fmt.Errorf("method SetServer requires a valid server implementation, got nil")
       |    }
       |
       |    v.server = s
       |    return nil
       |}
       |
       |func (v *$name) SetMarshaller(marshaller irt.Marshaller) error {
       |    if marshaller == nil {
       |        return fmt.Errorf("method SetMarshaller requires a valid marshaller, got nil")
       |    }
       |
       |    v.marshaller = marshaller
       |    return nil
       |}
       |
       |func (v *$name) GetSupportedService() string {
       |    return "${i.id.name}"
       |}
       |
       |func (v *$name) GetSupportedMethods() []string {
       |    return []string{
       |${i.methods.map(m => if (m.isInstanceOf[DefMethod.RPCMethod]) "\"" + m.asInstanceOf[DefMethod.RPCMethod].name + "\"," else "").mkString("\n").shift(8)}
       |    }
       |}
       |
       |func (v *$name) Dispatch(context interface{}, method string, data []byte) ([]byte, error) {
       |    switch method {
       |${i.methods.map(m => renderDispatcherHandler(i.id.name, m, "server")).mkString("\n").shift(8)}
       |        default:
       |            return nil, fmt.Errorf("$name dispatch doesn't support method %s", method)
       |    }
       |}
       |
       |func New${name}(server ${i.id.name}Server, marshaller irt.Marshaller) *$name{
       |    res := &$name{}
       |    res.SetServer(server)
       |    res.SetMarshaller(marshaller)
       |    return res
       |}
     """.stripMargin
  }

  protected def renderServiceMethodDefaultResult(method: DefMethod, imports: GoLangImports): String = {
    method match {
      case m: DefMethod.RPCMethod => m.signature.output match {
        case _: Struct => "nil"
        case _: Algebraic => "nil"
        case si: Singular => GoLangType(si.typeId, imports, ts).defaultValue()
        case _: Void => ""
        case _: Alternative => throw new Exception("Not implemented")
      }
    }
  }

  protected def renderServiceServerDummy(i: Service, imports: GoLangImports): String = {
    val name = s"${i.id.name}ServerDummy"
    s"""// $name is a dummy for implementation references
       |type $name struct {
       |    // Implements ${i.id.name}Server interface
       |}
       |
       |${i.methods.map(m => s"func (d *$name) " + renderRPCMethodSignature(i.id.name, m, imports, spread = true, withContext = true) + s""" {\n    return ${if (isServiceMethodOutputExistent(m.asInstanceOf[DefMethod.RPCMethod])) renderServiceMethodDefaultResult(m, imports) + ", " else ""}fmt.Errorf("Method not implemented.")\n}\n""").mkString("\n")}
     """.stripMargin
  }

  protected def renderServiceMethodOutModel(i: Service, name: String, out: DefMethod.Output, imports: GoLangImports): String = out match {
    case st: Struct => renderServiceMethodInModel(i, name, st.struct, imports)
    case al: Algebraic => renderAdtImpl(name, al.alternatives, imports, withTest = false)
    case si: Singular => s"// ${ si.typeId}"
    case _: Void => ""
    case _: Alternative => throw new Exception("Not implemented")
  }

  protected def renderServiceMethodInModel(i: Service, name: String, structure: SimpleStructure, imports: GoLangImports): String = {
    val struct = GoLangStruct(name, DTOId(i.id, name), List.empty,
      structure.fields.map(ef => GoLangField(ef.name, GoLangType(ef.typeId, imports, ts), name, imports, ts)),
      imports, ts
    )
    s"""${struct.render(withTest = false)}
       |${struct.renderSerialized()}
     """.stripMargin
  }

  protected def renderServiceMethodModels(i: Service, method: DefMethod, imports: GoLangImports): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""// Method ${m.name} models
         |${if(m.signature.input.fields.isEmpty) "" else renderServiceMethodInModel(i, inName(i.id.name, m.name, public = true), m.signature.input, imports)}
         |${renderServiceMethodOutModel(i, outName(i.id.name, m.name, public = true), m.signature.output, imports)}
       """.stripMargin

  }

  protected def renderServiceModels(i: Service, imports: GoLangImports): String = {
    i.methods.map(me => renderServiceMethodModels(i, me, imports)).mkString("\n")
  }

  protected def renderService(i: Service): RenderableCogenProduct = {
    val imports = GoLangImports(i, i.id.domain.toPackage, List.empty, options.manifest)
    val prefix = if (options.manifest.isDefined) GoLangBuildManifest.importPrefix(options.manifest.get).get else ""

    val svc =
        s"""// ============== Service models ==============
           |${renderServiceModels(i, imports)}
           |
           |// ============== Service Client ==============
           |${renderServiceClient(i, imports)}
           |
           |// ============== Service Dispatcher ==============
           |${renderServiceDispatcher(i, imports)}
           |
           |// ============== Service Server Dummy ==============
           |${renderServiceServerDummy(i, imports)}
         """.stripMargin

      ServiceProduct(svc, imports.renderImports(Seq("encoding/json", "fmt", "time", prefix + "irt")))
  }

  protected def renderEmitterModels(i: Emitter, imports: GoLangImports): String = {
    i.events.map(me => renderEmitterMethodModels(i, me, imports)).mkString("\n")
  }

  protected def renderEmitterMethodModels(i: Emitter, method: DefMethod, imports: GoLangImports): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""// Method ${m.name} models
         |${if(m.signature.input.fields.isEmpty) "" else renderEmitterMethodInModel(i, inName(i.id.name, m.name, public = true), m.signature.input, imports)}
         |${renderEmitterMethodOutModel(i, outName(i.id.name, m.name, public = true), m.signature.output, imports)}
       """.stripMargin
  }

  protected def renderEmitterMethodOutModel(i: Emitter, name: String, out: DefMethod.Output, imports: GoLangImports): String = out match {
    case st: Struct => renderEmitterMethodInModel(i, name, st.struct, imports)
    case al: Algebraic => renderAdtImpl(name, al.alternatives, imports, withTest = false)
    case si: Singular => s"// ${ si.typeId}"
    case _: Void => ""
    case _: Alternative => throw new Exception("Not implemented")
  }

  protected def renderEmitterMethodInModel(i: Emitter, name: String, structure: SimpleStructure, imports: GoLangImports): String = {
    val struct = GoLangStruct(name, DTOId(i.id, name), List.empty,
      structure.fields.map(ef => GoLangField(ef.name, GoLangType(ef.typeId, imports, ts), name, imports, ts)),
      imports, ts
    )
    s"""${struct.render(withTest = false)}
       |${struct.renderSerialized()}
     """.stripMargin
  }

  protected def renderEmitterClient(i: Emitter, imports: GoLangImports): String = {
    val name = s"${i.id.name}Client"

    s"""type ${i.id.name} interface {
       |${i.events.map(m => renderRPCMethodSignature(i.id.name, m, imports, spread = true)).mkString("\n").shift(4)}
       |}
       |
       |type $name struct {
       |    ${i.id.name}
       |    transport irt.ClientSocketTransport
       |}
       |
       |func (v *$name) SetTransport(t irt.ClientSocketTransport) error {
       |    if t == nil {
       |        return fmt.Errorf("method SetTransport requires a valid transport, got nil")
       |    }
       |
       |    v.transport = t
       |    return nil
       |}
       |
       |${i.events.map(me => renderRPCClientMethod(i.id.name, me, imports)).mkString("\n")}
     """.stripMargin
  }

  protected def renderEmitterDispatcher(i: Emitter, imports: GoLangImports): String = {
    val name = s"${i.id.name}Dispatcher"

    s"""type ${i.id.name}BuzzerHandlers interface {
       |${i.events.map(m => renderRPCMethodSignature(i.id.name, m, imports, spread = true, withContext = true)).mkString("\n").shift(4)}
       |}
       |
       |type $name struct {
       |    handlers ${i.id.name}BuzzerHandlers
       |    marshaller irt.Marshaller
       |}
       |
       |func (v *$name) SetHandlers(h ${i.id.name}BuzzerHandlers) error {
       |    if h == nil {
       |        return fmt.Errorf("method SetHandlers requires a valid handlers implementation, got nil")
       |    }
       |
       |    v.handlers = h
       |    return nil
       |}
       |
       |func (v *$name) SetMarshaller(marshaller irt.Marshaller) error {
       |    if marshaller == nil {
       |        return fmt.Errorf("method SetMarshaller requires a valid marshaller, got nil")
       |    }
       |
       |    v.marshaller = marshaller
       |    return nil
       |}
       |
       |func (v *$name) GetSupportedService() string {
       |    return "${i.id.name}"
       |}
       |
       |func (v *$name) GetSupportedMethods() []string {
       |    return []string{
       |${i.events.map(m => if (m.isInstanceOf[DefMethod.RPCMethod]) "\"" + m.asInstanceOf[DefMethod.RPCMethod].name + "\"," else "").mkString("\n").shift(8)}
       |    }
       |}
       |
       |func (v *$name) Dispatch(context interface{}, method string, data []byte) ([]byte, error) {
       |    switch method {
       |${i.events.map(m => renderDispatcherHandler(i.id.name, m, "handlers")).mkString("\n").shift(8)}
       |        default:
       |            return nil, fmt.Errorf("$name dispatch doesn't support method %s", method)
       |    }
       |}
       |
       |func New${name}(handlers ${i.id.name}BuzzerHandlers, marshaller irt.Marshaller) *$name{
       |    res := &$name{}
       |    res.SetHandlers(handlers)
       |    res.SetMarshaller(marshaller)
       |    return res
       |}
     """.stripMargin
  }

  protected def renderEmitterHandlersDummy(i: Emitter, imports: GoLangImports): String = {
    val name = s"${i.id.name}BuzzerHandlersDummy"
    s"""// $name is a dummy for implementation references
       |type $name struct {
       |    // Implements ${i.id.name}BuzzerHandlers interface
       |}
       |
       |${i.events.map(m => s"func (d *$name) " + renderRPCMethodSignature(i.id.name, m, imports, spread = true, withContext = true) + s""" {\n    return ${if (isServiceMethodOutputExistent(m.asInstanceOf[DefMethod.RPCMethod])) renderServiceMethodDefaultResult(m, imports) + ", " else ""}fmt.Errorf("Method not implemented.")\n}\n""").mkString("\n")}
     """.stripMargin
  }

  protected def renderEmitter(i: Emitter): RenderableCogenProduct = {
    val imports = GoLangImports(i, i.id.domain.toPackage, List.empty, options.manifest)
    val prefix = if (options.manifest.isDefined) GoLangBuildManifest.importPrefix(options.manifest.get).get else ""

    val svc =
      s"""// ============== Models ==============
         |${renderEmitterModels(i, imports)}
         |
         |// ============== Client ==============
         |${renderEmitterClient(i, imports)}
         |
         |// ============== Dispatcher ==============
         |${renderEmitterDispatcher(i, imports)}
         |
         |// ============== Handlers Dummy ==============
         |${renderEmitterHandlersDummy(i, imports)}
         """.stripMargin

    EmitterProduct(svc, imports.renderImports(Seq("encoding/json", "fmt", prefix + "irt")))
  }
}
