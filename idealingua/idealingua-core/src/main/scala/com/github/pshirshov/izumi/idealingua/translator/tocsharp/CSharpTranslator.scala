package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.Output.{Algebraic, Singular, Struct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.{CSharpTranslatorExtension, JsonNetExtension, Unity3DExtension}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.RenderableCogenProduct
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.csharp.types.CSharpField
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.CogenProduct._
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.types.{CSharpClass, CSharpType}
//import com.github.pshirshov.izumi.idealingua.translator.tocsharp.types._

object CSharpTranslator {
  final val defaultExtensions = Seq(
    JsonNetExtension,
    Unity3DExtension
  )
}

class CSharpTranslator(ts: Typespace, extensions: Seq[CSharpTranslatorExtension]) {
  protected val ctx: CSTContext = new CSTContext(ts, extensions)

  import ctx._

  def translate(): Seq[Module] = {
    val modules = Seq(
      typespace.domain.types.flatMap(translateDef)
      , typespace.domain.services.flatMap(translateService)
    ).flatten

    modules
  }


  protected def translateService(definition: Service): Seq[Module] = {
    ctx.modules.toSource(definition.id.domain, ctx.modules.toModuleId(definition.id), renderService(definition))
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

    ctx.modules.toSource(definition.id.path.domain, ctx.modules.toModuleId(definition), defns)
  }

  protected def renderDto(i: DTO): RenderableCogenProduct = {
//    val imports = GoLangImports(i, i.id.path.toPackage, ts)
//
//    val fields = typespace.structure.structure(i).all
//    val distinctFields = fields.groupBy(_.field.name).map(_._2.head.field)
//
//    val struct = GoLangStruct(
//      i.id.name,
//      i.id,
//      i.struct.superclasses.interfaces,
//      distinctFields.map(df => GoLangField(df.name, GoLangType(df.typeId, imports, ts), i.id.name, imports, ts)).toList,
//      imports,
//      ts
//    )
//
//    val dto =
//      s"""${struct.render()}
//         |${struct.renderSerialized()}
//         |${struct.renderSlices()}
//         |${renderRegistrations(ts.inheritance.allParents(i.id), i.id.name, imports)}
//       """.stripMargin
//
//    val testImports = GoLangImports(struct.fields.flatMap(f => if (f.tp.testValue() != "\"d71ec06e-4622-4663-abd0-de1470eb6b7d\"" && f.tp.testValue() != "nil")
//      GoLangImports.collectTypes(f.tp.id) else List.empty), i.id.path.toPackage, ts, List.empty)
//
//    val tests =
//      s"""${testImports.renderImports(Seq("testing", "encoding/json"))}
//         |
//         |func Test${i.id.name}JSONSerialization(t *testing.T) {
//         |    v1 := New${i.id.name}(${struct.fields.map(f => GoLangType(f.tp.id, testImports, ts).testValue()).mkString(", ")})
//         |    serialized, err := json.Marshal(v1)
//         |    if err != nil {
//         |        t.Fatalf("Type '%s' should serialize into JSON using Marshal. %s", "${i.id.name}", err.Error())
//         |    }
//         |    var v2 ${i.id.name}
//         |    err = json.Unmarshal(serialized, &v2)
//         |    if err != nil {
//         |        t.Fatalf("Type '%s' should deserialize from JSON using Unmarshal. %s", "${i.id.name}", err.Error())
//         |    }
//         |    serialized2, err := json.Marshal(&v2)
//         |    if err != nil {
//         |        t.Fatalf("Type '%s' should serialize into JSON using Marshal. %s", "${i.id.name}", err.Error())
//         |    }
//         |
//         |    if string(serialized) != string(serialized2) {
//         |        t.Errorf("type '%s' serialization to JSON and from it afterwards must return the same value. Got '%s' and '%s'", "${i.id.name}", string(serialized), string(serialized2))
//         |    }
//         |}
//       """.stripMargin
//
//    CompositeProduct(dto, imports.renderImports(List("encoding/json", "fmt")), tests)
      CompositeProduct("/*dto*/")

  }

  protected def renderAlias(i: Alias): RenderableCogenProduct = {
    implicit val ts: Typespace = this.ts
    implicit val imports = CSharpImports(i, i.id.path.toPackage)
    val cstype = CSharpType(i.target)

    AliasProduct(
      s"""// C# does not natively support full type aliases. They usually
         |// live only within the current file scope, making it impossible
         |// to make them type aliases within another namespace.
         |//
         |// Had it been fully supported, the code would be something like:
         |// using ${i.id.name} = ${cstype.renderType()}
         |//
         |// For the time being, please use the target type everywhere you need.
         """.stripMargin
    )
  }

//  protected def renderAdtMember(structName: String, member: AdtMember, im: GoLangImports): String = {
//    val serializationName =  member.name
//    val typeName = GoLangType(member.typeId, im, ts).renderType()
//
//    s"""func (v *$structName) Is$serializationName() bool {
//       |    return v.valueType == "$serializationName"
//       |}
//       |
//       |func (v *$structName) Get$serializationName() $typeName {
//       |    if !v.Is$serializationName() {
//       |        return nil
//       |    }
//       |
//       |    obj, ok := v.value.($typeName)
//       |    if !ok {
//       |        return nil
//       |    }
//       |
//       |    return obj
//       |}
//       |
//       |func (v *$structName) Set$serializationName(obj $typeName) {
//       |    v.value = obj
//       |    v.valueType = "$serializationName"
//       |}
//       |
//       |func New${structName}From${member.typeId.name}(v $typeName) *$structName {
//       |    res := &$structName{}
//       |    res.Set$serializationName(v)
//       |    return res
//       |}
//     """.stripMargin
//  }
//
//  protected def renderAdtImpl(name: String, alternatives: List[AdtMember], imports: GoLangImports, withTest: Boolean = true): String = {
//    val test =
//      s"""
//         |func NewTest$name() *$name {
//         |    res := &$name{}
//         |    res.Set${alternatives.head.name}(NewTest${alternatives.head.name}())
//         |    return res
//         |}
//         """.stripMargin
//
//    s"""type $name struct {
//       |    value interface{}
//       |    valueType string
//       |}
//       |
//         |${alternatives.map(al => renderAdtMember(name, al, imports)).mkString("\n")}
//       |
//         |${if (withTest) test else ""}
//       |
//         |func (v *$name) MarshalJSON() ([]byte, error) {
//       |    if v.value == nil {
//       |        return nil, fmt.Errorf("trying to serialize a non-initialized Adt $name")
//       |    }
//       |
//         |    serialized, err := json.Marshal(v.value)
//       |    if err != nil {
//       |        return nil, err
//       |    }
//       |
//         |    return json.Marshal(&map[string]json.RawMessage {
//       |      v.valueType: serialized,
//       |    })
//       |}
//       |
//         |func (v *$name) UnmarshalJSON(data []byte) error {
//       |    raw := map[string]json.RawMessage{}
//       |    if err := json.Unmarshal(data, &raw); err != nil {
//       |        return err
//       |    }
//       |
//         |    for className, content := range raw {
//       |        v.valueType = className
//       |        switch className {
//       |${alternatives.map(al => "case \"" + al.name + "\": {\n" + GoLangType(al.typeId, imports, ts).renderUnmarshal("content", "v.value = ").shift(4) + "\n    return nil\n}").mkString("\n").shift(12)}
//       |            default:
//       |                return fmt.Errorf("$name encountered an unknown type %s during deserialization", className)
//       |        }
//       |    }
//       |
//         |    return fmt.Errorf("$name expects a root key to be present, empty object found")
//       |}
//       """.stripMargin
//  }

  protected def renderAdt(i: Adt): RenderableCogenProduct = {
//    val imports = GoLangImports(i, i.id.path.toPackage, ts)
//    val name = i.id.name
//
//    val tests =
//      s"""import (
//         |    "testing"
//         |    "encoding/json"
//         |)
//         |
//         |${i.alternatives.map(al => renderAdtAlternativeTest(i, al)).mkString("\n")}
//       """.stripMargin
//
//    AdtProduct(renderAdtImpl(name, i.alternatives, imports), imports.renderImports(Seq("fmt", "encoding/json")), tests)
    CompositeProduct("/*adt*/")
  }

  protected def renderEnumeration(i: Enumeration): RenderableCogenProduct = {
      val name = i.id.name
      val decl =
        s"""// $name Enumeration
           |public enum $name {
           |${i.members.map(m => s"$m${if (m == i.members.last) "" else ","}").mkString("\n").shift(4)}
           |}
           |
           |public static class ${name}Helpers {
           |    public static $name From(string value) {
           |        $name v;
           |        if (Enum.TryParse(value, out v)) {
           |            return v;
           |        }
           |        throw new ArgumentOutOfRangeException(value);
           |    }
           |
           |    public static bool IsValid(string value) {
           |        return Enum.IsDefined(typeof($name), value);
           |    }
           |
           |    // The elements in the array are still changeable, please use with care.
           |    private static readonly $name[] all = new $name[] {
           |${i.members.map(m => s"$name.$m${if (m == i.members.last) "" else ","}").mkString("\n").shift(8)}
           |    };
           |
           |    public static $name[] GetAll() {
           |        return ${name}Helpers.all;
           |    }
           |
           |    // Extensions
           |
           |    public static string ToString(this $name e) {
           |        return Enum.GetName(typeof($name), e);
           |    }
           |}
         """.stripMargin

    EnumProduct(
      decl,
      "using System;",
      ""
    )
  }

  protected def renderIdentifier(i: Identifier): RenderableCogenProduct = {
      implicit val ts = this.ts
      implicit val imports = CSharpImports(i, i.id.path.toPackage)

      val fields = ts.structure.structure(i).all.map(f => CSharpField(f.field, i.id.name))
      val fieldsSorted = fields.sortBy(_.name)
      val csClass = CSharpClass(i.id, fields)
      val prefixLength = i.id.name.length + 1

      val decl =
        s"""${csClass.renderHeader()} {
           |    private static char[] idSplitter = new char[]{':'};
           |${csClass.render(withWrapper = false).shift(4)}
           |    public override string ToString() {
           |        var suffix = ${fieldsSorted.map(f => f.tp.renderToString(f.renderMemberName(), escape = true)).mkString(" + \":\" + ")};
           |        return "${i.id.name}#" + suffix;
           |    }
           |
           |    public static ${i.id.name} From(string value) {
           |        if (value == null) {
           |            throw new ArgumentNullException("value");
           |        }
           |
           |        if (!value.StartsWith("${i.id.name}#")) {
           |            throw new ArgumentException(string.Format("Expected identifier for type ${i.id.name}, got {0}", value));
           |        }
           |
           |        var parts = value.Substring($prefixLength, value.Length - $prefixLength).Split(idSplitter, StringSplitOptions.None);
           |        if (parts.Length != ${fields.length}) {
           |            throw new ArgumentException(string.Format("Expected identifier for type ${i.id.name} with ${fields.length} parts, got {0} in string {1}", parts.Length, value));
           |        }
           |
           |        var res = new ${i.id.name}();
           |${fieldsSorted.zipWithIndex.map { case (f, index) => s"res.${f.renderMemberName()} = ${f.tp.renderFromString(s"parts[$index]", unescape = true)};"}.mkString("\n").shift(8)}
           |        return res;
           |    }
           |}
         """.stripMargin


//    val struct = GoLangStruct(
//      i.id.name,
//      i.id,
//      List.empty,
//      sortedFields.map(sf => GoLangField(sf.field.name, GoLangType(sf.field.typeId, imports, ts), i.id.name, imports, ts)),
//      imports,
//      ts
//    )

//    val decl =
//      s"""${struct.render()}
//         |
//         |// String converts an identifier to a string
//         |func (v *${i.id.name}) String() string {
//         |    suffix := ${sortedFields.map(f => "url.QueryEscape(" + GoLangType(f.field.typeId).renderToString(s"v.${f.field.name}") + ")").mkString(" + \":\" + ")}
//         |    return "${i.id.name}#" + suffix
//         |}
//         |
//         |func (v *${i.id.name}) Serialize() string {
//         |    return v.String()
//         |}
//         |
//         |func (v *${i.id.name}) LoadSerialized(s string) error {
//         |    if !strings.HasPrefix(s, "${i.id.name}#") {
//         |        return fmt.Errorf("expected identifier for type ${i.id.name}, got %s", s)
//         |    }
//         |
//         |    parts := strings.Split(s[${i.id.name.length + 1}:], ":")
//         |    if len(parts) != ${struct.fields.length} {
//         |        return fmt.Errorf("expected identifier for type ${i.id.name} with ${struct.fields.length} parts, got %d in string %s", len(parts), s)
//         |    }
//         |
//         |${struct.fields.zipWithIndex.map { case (f, index) => f.tp.renderFromString(f.renderMemberName(false), s"parts[$index]", unescape = true) }.mkString("\n").shift(4)}
//         |
//         |${struct.fields.map(f => f.renderAssign("v", f.renderMemberName(false), serialized = false, optional = false)).mkString("\n").shift(4)}
//         |    return nil
//         |}
//         |
//         |// MarshalJSON serialization for the identifier
//         |func (v *${i.id.name}) MarshalJSON() ([]byte, error) {
//         |    buffer := bytes.NewBufferString(`"`)
//         |    buffer.WriteString(v.String())
//         |    buffer.WriteString(`"`)
//         |    return buffer.Bytes(), nil
//         |}
//         |
//         |// UnmarshalJSON deserialization for the identifier
//         |func (v *${i.id.name}) UnmarshalJSON(b []byte) error {
//         |    var s string
//         |    err := json.Unmarshal(b, &s)
//         |    if err != nil {
//         |        return err
//         |    }
//         |
//         |    return v.LoadSerialized(s)
//         |}
//       """.stripMargin


    IdentifierProduct(
      decl,
      "using System;"// imports.render(ts)
    )
  }

  protected def renderInterface(i: Interface): RenderableCogenProduct = {
//    val imports = GoLangImports(i, i.id.path.toPackage, ts)
//
//    val fields = typespace.structure.structure(i)
//    val distinctFields = fields.all.groupBy(_.field.name).map(_._2.head.field)
//    val eid = typespace.tools.implId(i.id)
//
//    val struct = GoLangStruct(
//      eid.name,
//      eid,
//      i.struct.superclasses.interfaces ++ List(i.id),
//      distinctFields.map(df => GoLangField(df.name, GoLangType(df.typeId, imports, ts), eid.name, imports, ts)).toList,
//      imports,
//      ts,
//      List(i.id)
//    )
//
//    val iface =
//      s"""type ${i.id.name} interface {
//         |${i.struct.superclasses.interfaces.map(ifc => s"// implements ${ifc.name} interface").mkString("\n").shift(4)}
//         |${struct.fields.map(f => f.renderInterfaceMethods()).mkString("\n").shift(4)}
//         |    GetPackageName() string
//         |    GetClassName() string
//         |    GetFullClassName() string
//         |}
//       """.stripMargin
//
//    val companion =
//      s"""${struct.render()}
//         |${struct.renderSerialized()}
//         |${struct.renderSlices()}
//         |
//         |// Polymorphic section below. If a new type to be registered, use Register${i.id.name} method
//         |// which will add it to the known list. You can also overwrite the existing registrations
//         |// in order to provide extended functionality on existing models, preserving the original class name.
//         |
//         |type ${i.id.name}Constructor func() ${i.id.name}
//         |
//         |func ctor${eid.name}() ${i.id.name} {
//         |    return &${eid.name}{}
//         |}
//         |
//         |var known${i.id.name}Polymorphic = map[string]${i.id.name}Constructor {
//         |    rtti${eid.name}FullClassName: ctor${eid.name},
//         |}
//         |
//         |// Register${i.id.name} registers a new constructor for a polymorphic type ${i.id.name}
//         |func Register${i.id.name}(className string, ctor ${i.id.name}Constructor) {
//         |    known${i.id.name}Polymorphic[className] = ctor
//         |}
//         |
//         |// Create${i.id.name} creates an instance of type ${i.id.name} in a polymorphic way
//         |func Create${i.id.name}(data map[string]json.RawMessage) (${i.id.name}, error) {
//         |    for className, content := range data {
//         |        ctor, ok := known${i.id.name}Polymorphic[className]
//         |        if !ok {
//         |            return nil, fmt.Errorf("unknown polymorphic type %s for Create${i.id.name}", className)
//         |        }
//         |
//         |        instance := ctor()
//         |        err := json.Unmarshal(content, instance)
//         |        if err != nil {
//         |            return nil, err
//         |        }
//         |
//         |        return instance, nil
//         |    }
//         |
//         |    return nil, fmt.Errorf("empty content for polymorphic type in Create${i.id.name}")
//         |}
//         |${renderRegistrations(ts.inheritance.allParents(i.id), eid.name, imports)}
//       """.stripMargin

//    InterfaceProduct(iface, companion, imports.renderImports(List("encoding/json", "fmt")), tests)
    CompositeProduct("/*iface*/")
  }

//  protected def renderServiceMethodSignature(i: Service, method: Service.DefMethod, imports: GoLangImports, spread: Boolean = false, withContext: Boolean = false): String = {
//    method match {
//      case m: DefMethod.RPCMethod => {
//        val context = if (withContext) s"context interface{}${if (m.signature.input.fields.isEmpty) "" else ", "}" else ""
//        if (spread) {
//          val fields = m.signature.input.fields.map(f => f.name + " " + GoLangType(f.typeId, imports, ts).renderType()).mkString(", ")
//          s"${m.name.capitalize}($context$fields) ${renderServiceMethodOutputSignature(i, m, imports)}"
//        } else {
//          s"${m.name.capitalize}(${context}input: ${inName(i, m.name)}) ${renderServiceMethodOutputSignature(i, m, imports)}"
//        }
//      }
//    }
//  }
//
//  protected def renderServiceMethodOutputModel(i: Service, method: DefMethod.RPCMethod, imports: GoLangImports): String = method.signature.output match {
//    case _: Struct => s"*${outName(i, method.name)}"
//    case _: Algebraic => s"*${outName(i, method.name)}"
//    case si: Singular => s"${GoLangType(si.typeId, imports, ts).renderType()}"
//  }
//
//  protected def renderServiceMethodOutputSignature(i: Service, method: DefMethod.RPCMethod, imports: GoLangImports): String = {
//    s"(${renderServiceMethodOutputModel(i, method, imports)}, error)"
//  }
//
//  protected def renderServiceClientMethod(i: Service, method: Service.DefMethod, imports: GoLangImports): String = method match {
//    case m: DefMethod.RPCMethod => m.signature.output match {
//      case _: Struct | _: Algebraic =>
//        s"""func (c *${i.id.name}Client) ${renderServiceMethodSignature(i, method, imports, spread = true)} {
//           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"inData := new${inName(i, m.name)}(${m.signature.input.fields.map(ff => ff.name).mkString(", ")})" }
//           |    outData := &${outName(i, m.name)}{}
//           |    err := c.transport.Send("${i.id.name}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "nil" else "inData"}, outData)
//           |    if err != nil {
//           |        return nil, err
//           |    }
//           |    return outData, nil
//           |}
//       """.stripMargin
//
//      case so: Singular =>
//        s"""func (c *${i.id.name}Client) ${renderServiceMethodSignature(i, method, imports, spread = true)} {
//           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"inData := new${inName(i, m.name)}(${m.signature.input.fields.map(ff => ff.name).mkString(", ")})" }
//           |    outData := &${GoLangType(so.typeId, imports, ts).renderType(forAlias = true)}
//           |    err := c.transport.Send("${i.id.name}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "nil" else "inData"}, outData)
//           |    if err != nil {
//           |        return nil, err
//           |    }
//           |    return outData, nil
//           |}
//       """.stripMargin
//    }
//  }
//
//  protected def renderServiceClient(i: Service, imports: GoLangImports): String = {
//    val name = s"${i.id.name}Client"
//
//    s"""type ${i.id.name} interface {
//       |${i.methods.map(m => renderServiceMethodSignature(i, m, imports, spread = true)).mkString("\n").shift(4)}
//       |}
//       |
//       |type $name struct {
//       |    ${i.id.name}
//       |    transport irt.ServiceClientTransport
//       |}
//       |
//       |func (v *$name) SetTransport(t irt.ServiceClientTransport) error {
//       |    if t == nil {
//       |        return fmt.Errorf("method SetTransport requires a valid transport, got nil")
//       |    }
//       |
//       |    v.transport = t
//       |    return nil
//       |}
//       |
//       |func (v *$name) SetHTTPTransport(endpoint string, timeout int, skipSSLVerify bool) {
//       |    v.transport = irt.NewHTTPClientTransport(endpoint, timeout, skipSSLVerify)
//       |}
//       |
//       |func New${name}OverHTTP(endpoint string) *$name{
//       |    res := &$name{}
//       |    res.SetHTTPTransport(endpoint, 15000, false)
//       |    return res
//       |}
//       |
//       |${i.methods.map(me => renderServiceClientMethod(i, me, imports)).mkString("\n")}
//     """.stripMargin
//  }
//
//  protected def renderServiceDispatcherHandler(i: Service, method: Service.DefMethod): String = method match {
//    case m: DefMethod.RPCMethod =>
//      s"""case "${m.name}": {
//         |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"dataIn, ok := data.(*${inName(i, m.name)})\n    if !ok {\n        return nil, fmt.Errorf(" + "\"invalid input data object for method " + m.name + "\")\n    }"}
//         |    return v.service.${m.name.capitalize}(context${if(m.signature.input.fields.isEmpty) "" else ", "}${m.signature.input.fields.map(f => s"dataIn.${f.name.capitalize}()").mkString(", ")})
//         |}
//         |
//       """.stripMargin
//  }
//
//  protected def renderServiceDispatcherPreHandler(i: Service, method: Service.DefMethod): String = method match {
//    case m: DefMethod.RPCMethod =>
//      s"""case "${m.name}": ${if (m.signature.input.fields.isEmpty) "return nil, nil" else s"return &${inName(i, m.name)}{}, nil"}""".stripMargin
//  }
//
//  protected def renderServiceDispatcher(i: Service, imports: GoLangImports): String = {
//    val name = s"${i.id.name}Dispatcher"
//
//    s"""type ${i.id.name}Server interface {
//       |${i.methods.map(m => renderServiceMethodSignature(i, m, imports, spread = true, withContext = true)).mkString("\n").shift(4)}
//       |}
//       |
//       |type $name struct {
//       |    service ${i.id.name}Server
//       |}
//       |
//       |func (v *$name) SetServer(s ${i.id.name}Server) error {
//       |    if s == nil {
//       |        return fmt.Errorf("method SetServer requires a valid server implementation, got nil")
//       |    }
//       |
//       |    v.service = s
//       |    return nil
//       |}
//       |
//       |func (v *$name) GetSupportedService() string {
//       |    return "${i.id.name}"
//       |}
//       |
//       |func (v *$name) GetSupportedMethods() []string {
//       |    return []string{
//       |${i.methods.map(m => if (m.isInstanceOf[DefMethod.RPCMethod]) "\"" + m.asInstanceOf[DefMethod.RPCMethod].name + "\"," else "").mkString("\n").shift(8)}
//       |    }
//       |}
//       |
//       |func (v *$name) PreDispatchModel(context interface{}, method string) (interface{}, error) {
//       |    switch method {
//       |${i.methods.map(m => renderServiceDispatcherPreHandler(i, m)).mkString("\n").shift(8)}
//       |        default:
//       |            return nil, fmt.Errorf("$name dispatch doesn't support method %s", method)
//       |    }
//       |}
//       |
//       |func (v *$name) Dispatch(context interface{}, method string, data interface{}) (interface{}, error) {
//       |    switch method {
//       |${i.methods.map(m => renderServiceDispatcherHandler(i, m)).mkString("\n").shift(8)}
//       |        default:
//       |            return nil, fmt.Errorf("$name dispatch doesn't support method %s", method)
//       |    }
//       |}
//       |
//       |func New${name}(service ${i.id.name}Server) *$name{
//       |    res := &$name{}
//       |    res.SetServer(service)
//       |    return res
//       |}
//     """.stripMargin
//  }
//
//  protected def renderServiceServerDummy(i: Service, imports: GoLangImports): String = {
//    val name = s"${i.id.name}ServerDummy"
//    s"""// $name is a dummy for implementation references
//       |type $name struct {
//       |    // Implements ${i.id.name}Server interface
//       |}
//       |
//       |${i.methods.map(m => s"func (d *$name) " + renderServiceMethodSignature(i, m, imports, spread = true, withContext = true) + s""" {\n    return nil, fmt.Errorf("Method not implemented.")\n}\n""").mkString("\n")}
//     """.stripMargin
//  }
//
//  protected def renderServiceMethodOutModel(i: Service, name: String, out: Service.DefMethod.Output, imports: GoLangImports): String = out match {
//    case st: Struct => renderServiceMethodInModel(i, name, st.struct, imports)
//    case al: Algebraic => renderAdtImpl(name, al.alternatives, imports, withTest = false)
//    case _ => s""
//  }
//
//  protected def renderServiceMethodInModel(i: Service, name: String, structure: SimpleStructure, imports: GoLangImports): String = {
//    val struct = GoLangStruct(name, DTOId(i.id, name), List.empty,
//      structure.fields.map(ef => GoLangField(ef.name, GoLangType(ef.typeId, imports, ts), name, imports, ts)),
//      imports, ts
//    )
//    s"""${struct.render(makePrivate = true, withTest = false)}
//       |${struct.renderSerialized(makePrivate = true)}
//     """.stripMargin
//  }
//
//  protected def renderServiceMethodModels(i: Service, method: Service.DefMethod, imports: GoLangImports): String = method match {
//    case m: DefMethod.RPCMethod =>
//      s"""${if(m.signature.input.fields.isEmpty) "" else renderServiceMethodInModel(i, inName(i, m.name), m.signature.input, imports)}
//         |${renderServiceMethodOutModel(i, outName(i, m.name), m.signature.output, imports)}
//       """.stripMargin
//
//  }
//
//  protected def renderServiceModels(i: Service, imports: GoLangImports): String = {
//    i.methods.map(me => renderServiceMethodModels(i, me, imports)).mkString("\n")
//  }

  protected def renderService(i: Service): RenderableCogenProduct = {
//    val imports = GoLangImports(i, i.id.domain.toPackage, List.empty)
//
//    val svc =
//      s"""// ============== Service models ==============
//         |${renderServiceModels(i, imports)}
//         |
//           |// ============== Service Client ==============
//         |${renderServiceClient(i, imports)}
//         |
//           |// ============== Service Dispatcher ==============
//         |${renderServiceDispatcher(i, imports)}
//         |
//           |// ============== Service Server Dummy ==============
//         |${renderServiceServerDummy(i, imports)}
//         """.stripMargin
//
//    ServiceProduct(svc, imports.renderImports(Seq("encoding/json", "fmt", "irt")))
    CompositeProduct("/*service*/")
  }
}

