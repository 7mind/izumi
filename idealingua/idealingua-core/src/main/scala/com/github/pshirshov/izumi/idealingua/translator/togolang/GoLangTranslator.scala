package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.Output.{Algebraic, Singular, Struct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.togolang.extensions.GoLangTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.togolang.products.RenderableCogenProduct
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.togolang.products.CogenProduct._
import com.github.pshirshov.izumi.idealingua.translator.togolang.types._

object GoLangTranslator {
  final val defaultExtensions = Seq(
  )
}

class GoLangTranslator(ts: Typespace, extensions: Seq[GoLangTranslatorExtension]) {
  protected val ctx: GLTContext = new GLTContext(ts, extensions)

  import ctx._

  def translate(): Seq[Module] = {
    val modules = Seq(
      typespace.domain.types.flatMap(translateDef)
      , typespace.domain.services.flatMap(translateService)
    ).flatten

    modules
  }


  protected def translateService(definition: Service): Seq[Module] = {
    ctx.modules.toSource(definition.id.domain, ctx.modules.toModuleId(definition.id),
      ctx.modules.toTestModuleId(definition.id), renderService(definition))
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

    s"""${interfaces.map(sc => renderRegistrationCtor(sc, structName, imports)).mkString("\n")}
       |
       |func init() {
       |    // Here we register current DTO in other interfaces
       |${interfaces.map(sc => s"${imports.withImport(sc)}Register${sc.name}(rtti${structName}FullClassName, ctor${structName}For${sc.name})").mkString("\n").shift(4)}
       |}
     """.stripMargin
  }

  protected def renderDto(i: DTO): RenderableCogenProduct = {
    val imports = GoLangImports(i, i.id.path.toPackage, ts)

    val fields = typespace.structure.structure(i).all
    val distinctFields = fields.groupBy(_.field.name).map(_._2.head.field)

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
         |${renderRegistrations(ts.inheritance.allParents(i.id), i.id.name, imports)}
       """.stripMargin

    val testImports = GoLangImports(struct.fields.flatMap(f => if (f.tp.testValue() != "nil") GoLangImports.collectTypes(f.tp.id) else List.empty), i.id.path.toPackage, ts, List.empty)

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
      val imports = GoLangImports(i, i.id.path.toPackage, ts)
      val goType = GoLangType(i.target, imports, ts)

      AliasProduct(
        s"""// ${i.id.name} alias
           |type ${i.id.name} = ${goType.renderType(forAlias = true)}
         """.stripMargin,
        imports.renderImports()
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

  protected def renderAdtImpl(name: String, alternatives: List[AdtMember], imports: GoLangImports, withTest: Boolean = true): String = {
      val test =
        s"""
           |func NewTest$name() *$name {
           |    res := &$name{}
           |    res.Set${alternatives.head.name}(NewTest${alternatives.head.name}())
           |    return res
           |}
         """.stripMargin

      s"""type $name struct {
         |    value interface{}
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
         |    serialized, err := json.Marshal(v.value)
         |    if err != nil {
         |        return nil, err
         |    }
         |
         |    return json.Marshal(&map[string]json.RawMessage {
         |      v.valueType: serialized,
         |    })
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
         |                return fmt.Errorf("$name encountered an unknown type %s during deserialization", className)
         |        }
         |    }
         |
         |    return fmt.Errorf("$name expects a root key to be present, empty object found")
         |}
       """.stripMargin
  }

  protected def renderAdtAlternativeTest(i: Adt, m: AdtMember): String = {
    s"""func Test${i.id.name}As${m.name}(t *testing.T) {
       |    adt := &${i.id.name}{}
       |    adt.Set${m.name}(NewTest${m.typeId.name}())
       |
       |    if !adt.Is${m.name}() {
       |        t.Errorf("type '%s' Is${m.name} must be true.", "${i.id.name}")
       |    }
       |
       |${i.alternatives.map(al => if (al.name == m.name) "" else s"""if adt.Is${al.name} {\n    t.Errorf("type '%s' Is${al.name} must be false.", "${i.id.name}")""").mkString("\n").shift(4)}
       |
       |    serialized, err := json.Marshal(adt)
       |    if err != nil {
       |        t.Errorf("type '%s' json serialization failed. %+v", "${i.id.name}", err)
       |    }
       |
       |    adt2 := &${i.id.name}{}
       |    err = json.Unmarshal(serialized, adt2)
       |    if err != nil {
       |        t.Errorf("type '%s' json deserialization failed. %+v", "${i.id.name}", err)
       |    }
       |
       |    if !adt2.Is${m.name}() {
       |        t.Errorf("type '%s' Is${m.name} must be true after deserialization.", "${i.id.name}")
       |    }
       |}
     """.stripMargin
  }

  protected def renderAdt(i: Adt): RenderableCogenProduct = {
    val imports = GoLangImports(i, i.id.path.toPackage, ts)
    val name = i.id.name

    val tests =
      s"""import (
         |    "testing"
         |    "encoding/json"
         |)
         |
         |${i.alternatives.map(al => renderAdtAlternativeTest(i, al)).mkString("\n")}
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
       |${i.members.map(m => s"// $m enum value\n" + (if (m == i.members.head) s"$m $name = iota" else m)).mkString("\n").shift(4)}
       |)
       |
       |var map${name}ToString = map[$name]string{
       |${i.members.map(m => s"$m: " + "\"" + m + "\",").mkString("\n").shift(4)}
       |}
       |
       |var mapStringTo$name = map[string]$name{
       |${i.members.map(m => "\"" + m + "\": " + s"$m,").mkString("\n").shift(4)}
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
         |        t.Errorf("type '%s' should be possible to create via New method with '%s' value", "$name", "${i.members.head}")
         |    }
         |
         |    v2 := ${i.members.head}
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
      GoLangImports(List.empty).renderImports(Seq("encoding/json", "bytes")),
      tests
    )
  }

  protected def renderIdentifier(i: Identifier): RenderableCogenProduct = {
    val imports = GoLangImports(i, i.id.path.toPackage, ts)

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
    val imports = GoLangImports(i, i.id.path.toPackage, ts)

    val fields = typespace.structure.structure(i)
    val distinctFields = fields.all.groupBy(_.field.name).map(_._2.head.field)
    val eid = typespace.implId(i.id)

    val struct = GoLangStruct(
      eid.name,
      eid,
      i.struct.superclasses.interfaces ++ List(i.id),
      distinctFields.map(df => GoLangField(df.name, GoLangType(df.typeId, imports, ts), eid.name, imports, ts)).toList,
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
         |func ctor${eid.name}() ${i.id.name} {
         |    return &${eid.name}{}
         |}
         |
         |var known${i.id.name}Polymorphic = map[string]${i.id.name}Constructor {
         |    rtti${eid.name}FullClassName: ctor${eid.name},
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
         |            return nil, fmt.Errorf("unknown polymorphic type %s for Create${i.id.name}", className)
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
         |${renderRegistrations(ts.inheritance.allParents(i.id), eid.name, imports)}
       """.stripMargin

    val testImports = GoLangImports(struct.fields.flatMap(f => if (f.tp.testValue() != "nil") GoLangImports.collectTypes(f.tp.id) else List.empty), i.id.path.toPackage, ts, List.empty)

    val tests =
      s"""${testImports.renderImports(Seq("testing", "encoding/json"))}
         |
         |func Test${i.id.name}Creation(t *testing.T) {
         |    v := New${ts.implId(i.id).name}(${struct.fields.map(f => f.tp.testValue()).mkString(", ")})
         |    if v == nil {
         |        t.Errorf("interface of type ${i.id.name} should be possible to create with New method.")
         |    }
         |}
         |
         |func Test${i.id.name}JSONSerialization(t *testing.T) {
         |    v1 := New${ts.implId(i.id).name}(${struct.fields.map(f => f.tp.testValue()).mkString(", ")})
         |    serialized, err := json.Marshal(v1)
         |    if err != nil {
         |        t.Fatalf("Type '%s' should serialize into JSON using Marshal. %s", "${i.id.name}", err.Error())
         |    }
         |    var v2 ${ts.implId(i.id).name}
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

  protected def inName(i: Service, name: String, public: Boolean = false): String = {
    if (public)
      s"In${i.id.name.capitalize}${name.capitalize}"
    else
      s"in${i.id.name.capitalize}${name.capitalize}"
  }

  protected def outName(i: Service, name: String, public: Boolean = true): String = {
    if (public)
      s"Out${i.id.name.capitalize}${name.capitalize}"
    else
      s"out${i.id.name.capitalize}${name.capitalize}"
  }

  protected def renderServiceMethodSignature(i: Service, method: Service.DefMethod, imports: GoLangImports, spread: Boolean = false, withContext: Boolean = false): String = {
    method match {
      case m: DefMethod.RPCMethod => {
        val context = if (withContext) s"context interface{}${if (m.signature.input.fields.isEmpty) "" else ", "}" else ""
        if (spread) {
          val fields = m.signature.input.fields.map(f => f.name + " " + GoLangType(f.typeId, imports, ts).renderType()).mkString(", ")
          s"${m.name.capitalize}($context$fields) ${renderServiceMethodOutputSignature(i, m, imports)}"
        } else {
          s"${m.name.capitalize}(${context}input: ${inName(i, m.name)}) ${renderServiceMethodOutputSignature(i, m, imports)}"
        }
      }
    }
  }

  protected def renderServiceMethodOutputModel(i: Service, method: DefMethod.RPCMethod, imports: GoLangImports): String = method.signature.output match {
    case _: Struct => s"*${outName(i, method.name)}"
    case _: Algebraic => s"*${outName(i, method.name)}"
    case si: Singular => s"${GoLangType(si.typeId, imports, ts).renderType()}"
  }

  protected def renderServiceMethodOutputSignature(i: Service, method: DefMethod.RPCMethod, imports: GoLangImports): String = {
    s"(${renderServiceMethodOutputModel(i, method, imports)}, error)"
  }

  protected def renderServiceClientMethod(i: Service, method: Service.DefMethod, imports: GoLangImports): String = method match {
    case m: DefMethod.RPCMethod => m.signature.output match {
      case _: Struct | _: Algebraic =>
        s"""func (c *${i.id.name}Client) ${renderServiceMethodSignature(i, method, imports, spread = true)} {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"inData := new${inName(i, m.name)}(${m.signature.input.fields.map(ff => ff.name).mkString(", ")})" }
           |    outData := &${outName(i, m.name)}{}
           |    err := c.transport.Send("${i.id.name}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "nil" else "inData"}, outData)
           |    if err != nil {
           |        return nil, err
           |    }
           |    return outData, nil
           |}
       """.stripMargin

      case so: Singular =>
        s"""func (c *${i.id.name}Client) ${renderServiceMethodSignature(i, method, imports, spread = true)} {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"inData := new${inName(i, m.name)}(${m.signature.input.fields.map(ff => ff.name).mkString(", ")})" }
           |    outData := &${GoLangType(so.typeId, imports, ts).renderType(forAlias = true)}
           |    err := c.transport.Send("${i.id.name}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "nil" else "inData"}, outData)
           |    if err != nil {
           |        return nil, err
           |    }
           |    return outData, nil
           |}
       """.stripMargin
    }
  }

  protected def renderServiceClient(i: Service, imports: GoLangImports): String = {
    val name = s"${i.id.name}Client"

    s"""type ${i.id.name} interface {
       |${i.methods.map(m => renderServiceMethodSignature(i, m, imports, spread = true)).mkString("\n").shift(4)}
       |}
       |
       |type $name struct {
       |    ${i.id.name}
       |    transport irt.ServiceClientTransport
       |}
       |
       |func (v *$name) SetTransport(t irt.ServiceClientTransport) error {
       |    if t == nil {
       |        return fmt.Errorf("method SetTransport requires a valid transport, got nil")
       |    }
       |
       |    v.transport = t
       |    return nil
       |}
       |
       |func (v *$name) SetHTTPTransport(endpoint string, timeout int, skipSSLVerify bool) {
       |    v.transport = irt.NewHTTPClientTransport(endpoint, timeout, skipSSLVerify)
       |}
       |
       |func New${name}OverHTTP(endpoint string) *$name{
       |    res := &$name{}
       |    res.SetHTTPTransport(endpoint, 15000, false)
       |    return res
       |}
       |
       |${i.methods.map(me => renderServiceClientMethod(i, me, imports)).mkString("\n")}
     """.stripMargin
  }

  protected def renderServiceDispatcherHandler(i: Service, method: Service.DefMethod): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""case "${m.name}": {
         |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"dataIn, ok := data.(*${inName(i, m.name)})\n    if !ok {\n        return nil, fmt.Errorf(" + "\"invalid input data object for method " + m.name + "\")\n    }"}
         |    return v.service.${m.name.capitalize}(context${if(m.signature.input.fields.isEmpty) "" else ", "}${m.signature.input.fields.map(f => s"dataIn.${f.name.capitalize}()").mkString(", ")})
         |}
         |
       """.stripMargin
  }

  protected def renderServiceDispatcherPreHandler(i: Service, method: Service.DefMethod): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""case "${m.name}": ${if (m.signature.input.fields.isEmpty) "return nil, nil" else s"return &${inName(i, m.name)}{}, nil"}""".stripMargin
  }

  protected def renderServiceDispatcher(i: Service, imports: GoLangImports): String = {
    val name = s"${i.id.name}Dispatcher"

    s"""type ${i.id.name}Server interface {
       |${i.methods.map(m => renderServiceMethodSignature(i, m, imports, spread = true, withContext = true)).mkString("\n").shift(4)}
       |}
       |
       |type $name struct {
       |    service ${i.id.name}Server
       |}
       |
       |func (v *$name) SetServer(s ${i.id.name}Server) error {
       |    if s == nil {
       |        return fmt.Errorf("method SetServer requires a valid server implementation, got nil")
       |    }
       |
       |    v.service = s
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
       |func (v *$name) PreDispatchModel(context interface{}, method string) (interface{}, error) {
       |    switch method {
       |${i.methods.map(m => renderServiceDispatcherPreHandler(i, m)).mkString("\n").shift(8)}
       |        default:
       |            return nil, fmt.Errorf("$name dispatch doesn't support method %s", method)
       |    }
       |}
       |
       |func (v *$name) Dispatch(context interface{}, method string, data interface{}) (interface{}, error) {
       |    switch method {
       |${i.methods.map(m => renderServiceDispatcherHandler(i, m)).mkString("\n").shift(8)}
       |        default:
       |            return nil, fmt.Errorf("$name dispatch doesn't support method %s", method)
       |    }
       |}
       |
       |func New${name}(service ${i.id.name}Server) *$name{
       |    res := &$name{}
       |    res.SetServer(service)
       |    return res
       |}
     """.stripMargin
  }

  protected def renderServiceServerDummy(i: Service, imports: GoLangImports): String = {
    val name = s"${i.id.name}ServerDummy"
    s"""// $name is a dummy for implementation references
       |type $name struct {
       |    // Implements ${i.id.name}Server interface
       |}
       |
       |${i.methods.map(m => s"func (d *$name) " + renderServiceMethodSignature(i, m, imports, spread = true, withContext = true) + s""" {\n    return nil, fmt.Errorf("Method not implemented.")\n}\n""").mkString("\n")}
     """.stripMargin
  }

  protected def renderServiceMethodOutModel(i: Service, name: String, out: Service.DefMethod.Output, imports: GoLangImports): String = out match {
    case st: Struct => renderServiceMethodInModel(i, name, st.struct, imports)
    case al: Algebraic => renderAdtImpl(name, al.alternatives, imports, withTest = false)
    case _ => s""
  }

  protected def renderServiceMethodInModel(i: Service, name: String, structure: SimpleStructure, imports: GoLangImports): String = {
    val struct = GoLangStruct(name, DTOId(i.id, name), List.empty,
      structure.fields.map(ef => GoLangField(ef.name, GoLangType(ef.typeId, imports, ts), name, imports, ts)),
      imports, ts
    )
    s"""${struct.render(makePrivate = true, withTest = false)}
       |${struct.renderSerialized(makePrivate = true)}
     """.stripMargin
  }

  protected def renderServiceMethodModels(i: Service, method: Service.DefMethod, imports: GoLangImports): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""${if(m.signature.input.fields.isEmpty) "" else renderServiceMethodInModel(i, inName(i, m.name), m.signature.input, imports)}
         |${renderServiceMethodOutModel(i, outName(i, m.name), m.signature.output, imports)}
       """.stripMargin

  }

  protected def renderServiceModels(i: Service, imports: GoLangImports): String = {
    i.methods.map(me => renderServiceMethodModels(i, me, imports)).mkString("\n")
  }

  protected def renderService(i: Service): RenderableCogenProduct = {
      val imports = GoLangImports(i, i.id.domain.toPackage, List.empty)

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

      ServiceProduct(svc, imports.renderImports(Seq("encoding/json", "fmt", "irt")))
  }
}

