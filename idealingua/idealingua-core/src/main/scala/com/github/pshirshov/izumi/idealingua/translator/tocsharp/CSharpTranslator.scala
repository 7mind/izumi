package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, DomainId, TypePath}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.Output.{Algebraic, Singular, Struct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.{CSharpTranslatorExtension, JsonNetExtension, NUnitExtension}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.CogenProduct._
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.RenderableCogenProduct
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.types.{CSharpClass, CSharpField, CSharpType}

object CSharpTranslator {
  final val defaultExtensions = Seq(
    JsonNetExtension,
    NUnitExtension
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
    implicit val ts: Typespace = this.ts
    implicit val imports: CSharpImports = CSharpImports(definition, definition.id.domain.toPackage, List.empty)
    ctx.modules.toSource(definition.id.domain, ctx.modules.toModuleId(definition.id), renderService(definition))
  }

  protected def translateDef(definition: TypeDef): Seq[Module] = {
    implicit val ts: Typespace = this.ts
    implicit val imports: CSharpImports = CSharpImports(definition, definition.id.path.toPackage)

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

    ctx.modules.toSource(definition.id.path.domain, ctx.modules.toModuleId(definition), defns) ++ ext.postEmitModules(ctx, definition)
  }

  protected def renderDto(i: DTO)(implicit im: CSharpImports, ts: Typespace): RenderableCogenProduct = {
    val structure = ts.structure.structure(i)
    val struct = CSharpClass(i.id, i.id.name, structure, List.empty)

    val dto =
      s"""${im.renderUsings()}
         |${ext.preModelEmit(ctx, i)}
         |${struct.renderHeader()} {
         |${struct.render(withWrapper = false, withSlices = true, withRTTI = true).shift(4)}
         |}
         |${ext.postModelEmit(ctx, i)}
       """.stripMargin

    CompositeProduct(dto, im.renderImports(List("System", "System.Collections", "System.Collections.Generic") ++ ext.imports(ctx, i).toList))
  }

  protected def renderAlias(i: Alias)(implicit im: CSharpImports, ts: Typespace): RenderableCogenProduct = {
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

  protected def renderAdtMember(adtName: String, member: AdtMember)(implicit im: CSharpImports, ts: Typespace): String = {
    val operators =
      s"""    public static explicit operator _${member.name}(${member.name} m) {
         |        return m.Value;
         |    }
         |
         |    public static explicit operator ${member.name}(_${member.name} m) {
         |        return new ${member.name}(m);
         |    }
       """.stripMargin

    var operatorsDummy =
      s"""    // We would normally want to have an operator, but unfortunately if it is an interface,
         |    // it will fail on "user-defined conversions to or from an interface are now allowed".
         |    // public static explicit operator _${member.name}(${member.name} m) {
         |    //     return m.Value;
         |    // }
         |    //
         |    // public static explicit operator ${member.name}(_${member.name} m) {
         |    //     return new ${member.name}(m);
         |    // }
       """.stripMargin

    val memberType = CSharpType(member.typeId)
    s"""public sealed class ${member.name}: ${adtName} {
       |    public _${member.name} Value { get; private set; }
       |    public ${member.name}(_${member.name} value) {
       |        this.Value = value;
       |    }
       |
       |${if (member.typeId.isInstanceOf[InterfaceId]) operatorsDummy else operators}
       |}
     """.stripMargin
  }

  protected def renderAdtUsings(m: AdtMember)(implicit im: CSharpImports, ts: Typespace): String = {
    s"using _${m.name} = ${CSharpType(m.typeId).renderType()};"
  }

  protected def renderAdtImpl(adtName: String, members: List[AdtMember], renderUsings: Boolean = true)(implicit im: CSharpImports, ts: Typespace): String = {
    val adt = Adt(AdtId(TypePath(DomainId.Undefined, Seq.empty), adtName), members)
    s"""${im.renderUsings()}
       |${if (renderUsings) members.map(m => renderAdtUsings(m)).mkString("\n") else ""}
       |
       |${ext.preModelEmit(ctx, adt)}
       |public abstract class $adtName {
       |    private $adtName() {}
       |${members.map(m => renderAdtMember(adtName, m)).mkString("\n").shift(4)}
       |}
       |${ext.postModelEmit(ctx, adt)}
     """.stripMargin
  }

  protected def renderAdt(i: Adt)(implicit im: CSharpImports, ts: Typespace): RenderableCogenProduct = {

    AdtProduct(renderAdtImpl(i.id.name, i.alternatives), im.renderImports(ext.imports(ctx, i).toList))
  }

  protected def renderEnumeration(i: Enumeration)(implicit im: CSharpImports, ts: Typespace): RenderableCogenProduct = {
    val name = i.id.name
    val decl =
      s"""// $name Enumeration
         |public enum $name {
         |${i.members.map(m => s"$m${if (m == i.members.last) "" else ","}").mkString("\n").shift(4)}
         |}
         |
         |public static class ${name}Helpers {
         |    public static $name From(string value) {
         |        return ($name)Enum.Parse(typeof($name), value);
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
         |
           |${ext.postModelEmit(ctx, i)}
         """.stripMargin

    EnumProduct(
      decl,
      im.renderImports(List("System") ++ ext.imports(ctx, i)),
      ""
    )
  }

  protected def renderIdentifier(i: Identifier)(implicit im: CSharpImports, ts: Typespace): RenderableCogenProduct = {

    val fields = ts.structure.structure(i).all.map(f => CSharpField(f.field, i.id.name))
    val fieldsSorted = fields.sortBy(_.name)
    val csClass = CSharpClass(i.id, i.id.name, fields)
    val prefixLength = i.id.name.length + 1

    val decl =
      s"""${im.renderUsings()}
         |${ext.preModelEmit(ctx, i)}
         |${csClass.renderHeader()} {
         |    private static char[] idSplitter = new char[]{':'};
         |${csClass.render(withWrapper = false, withSlices = false, withRTTI = true).shift(4)}
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
         |${fieldsSorted.zipWithIndex.map { case (f, index) => s"res.${f.renderMemberName()} = ${f.tp.renderFromString(s"parts[$index]", unescape = true)};" }.mkString("\n").shift(8)}
         |        return res;
         |    }
         |}
         |
           |${ext.postModelEmit(ctx, i)}
         """.stripMargin

    IdentifierProduct(
      decl,
      im.renderImports(List("System", "System.Collections", "System.Collections.Generic") ++ ext.imports(ctx, i))
    )
  }

  protected def renderInterface(i: Interface)(implicit im: CSharpImports, ts: Typespace): RenderableCogenProduct = {
    val structure = typespace.structure.structure(i)
    val eid = typespace.tools.implId(i.id)

    val parentIfaces = ts.inheritance.parentsInherited(i.id).filter(_ != i.id)
    val validFields = structure.all.filterNot(f => parentIfaces.contains(f.defn.definedBy))
    val ifaceFields = validFields.map(f =>
      (if (f.defn.variance.nonEmpty) true else false, CSharpField(/*if (f.defn.variance.nonEmpty) f.defn.variance.last else */ f.field, eid.name, Seq.empty)))

    val struct = CSharpClass(eid, i.id.name + eid.name, structure, List(i.id))
    val ifaceImplements = if (i.struct.superclasses.interfaces.isEmpty) ": IRTTI" else ": " +
      i.struct.superclasses.interfaces.map(ifc => ifc.name).mkString(", ") + ", IRTTI"
    val dto = DTO(eid, Structure(validFields.map(f => f.field), List.empty, Super(List(i.id), List.empty, List.empty)))

    val iface =
      s"""${im.renderUsings()}
         |${ext.preModelEmit(ctx, i)}
         |public interface ${i.id.name}$ifaceImplements {
         |${ifaceFields.map(f => s"${if (f._1) "// Would have been covariance, but C# doesn't support it:\n// " else ""}${f._2.renderMember(true)}").mkString("\n").shift(4)}
         |}
         |${ext.postModelEmit(ctx, i)}
       """.stripMargin

    val companion =
      s"""${ext.preModelEmit(ctx, dto)}
         |${struct.renderHeader()} {
         |${struct.render(withWrapper = false, withSlices = true, withRTTI = true, withCTORs = Some(i.id.name)).shift(4)}
         |}
         |${ext.postModelEmit(ctx, dto)}
       """.stripMargin

    InterfaceProduct(iface, companion, im.renderImports(List("IRT", "System", "System.Collections", "System.Collections.Generic") ++ ext.imports(ctx, i).toList))
  }

  protected def renderServiceMethodSignature(i: Service, method: Service.DefMethod, forClient: Boolean)
                                            (implicit imports: CSharpImports, ts: Typespace): String = {
    val callbacks = s""
    method match {
      case m: DefMethod.RPCMethod => {
        val callback = s"${if (m.signature.input.fields.isEmpty) "" else ", "}Action<${renderServiceMethodOutputSignature(i, m)}> onSuccess, Action<Exception> onFailure, Action onAny = null, C ctx = null"
        val fields = m.signature.input.fields.map(f => CSharpType(f.typeId).renderType() + " " + f.name).mkString(", ")
        val context = s"C ctx${if (m.signature.input.fields.isEmpty) "" else ", "}"
        if (forClient) {
          s"void ${m.name.capitalize}($fields$callback)"
        } else {
          s"${renderServiceMethodOutputSignature(i, m)} ${m.name.capitalize}($context$fields)"
        }
      }
    }
  }

  protected def renderServiceMethodOutputModel(i: Service, method: DefMethod.RPCMethod)(implicit imports: CSharpImports, ts: Typespace): String = method.signature.output match {
    case _: Struct => s"${i.id.name}.Out${method.name.capitalize}"
    case _: Algebraic => s"${i.id.name}.Out${method.name.capitalize}"
    case si: Singular => s"${CSharpType(si.typeId).renderType()}"
  }

  protected def renderServiceMethodOutputSignature(i: Service, method: DefMethod.RPCMethod)(implicit imports: CSharpImports, ts: Typespace): String = {
    s"${renderServiceMethodOutputModel(i, method)}"
  }

  protected def renderServiceClientMethod(i: Service, method: Service.DefMethod)(implicit imports: CSharpImports, ts: Typespace): String = method match {
    case m: DefMethod.RPCMethod => m.signature.output match {
      case _: Struct | _: Algebraic =>
        s"""public ${renderServiceMethodSignature(i, method, forClient = true)} {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"var inData = new ${i.id.name}.In${m.name.capitalize}(${m.signature.input.fields.map(ff => ff.name).mkString(", ")});"}
           |    Transport.Send<${if (m.signature.input.fields.nonEmpty) s"${i.id.name}.In${m.name.capitalize}" else "object"}, ${renderServiceMethodOutputModel(i, m)}>("${i.id.name}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "null" else "inData"},
           |        new ClientTransportCallback<${renderServiceMethodOutputModel(i, m)}>(onSuccess, onFailure, onAny), ctx);
           |}
       """.stripMargin

      case so: Singular =>
        s"""public ${renderServiceMethodSignature(i, method, forClient = true)} {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"var inData = new ${i.id.name}.In${m.name.capitalize}(${m.signature.input.fields.map(ff => ff.name).mkString(", ")});"}
           |    Transport.Send<${if (m.signature.input.fields.nonEmpty) s"${i.id.name}.In${m.name.capitalize}" else "object"}, ${renderServiceMethodOutputModel(i, m)}>("${i.id.name}", "${m.name}", ${if (m.signature.input.fields.isEmpty) "null" else "inData"},
           |        new ClientTransportCallback<${renderServiceMethodOutputModel(i, m)}>(onSuccess, onFailure, onAny), ctx);
           |}
       """.stripMargin
    }
  }

  protected def renderServiceClient(i: Service)(implicit imports: CSharpImports, ts: Typespace): String = {
    val name = s"${i.id.name}Client"

    s"""public interface I${name}<C> where C: class, IClientTransportContext {
       |${i.methods.map(m => renderServiceMethodSignature(i, m, forClient = true) + ";").mkString("\n").shift(4)}
       |}
       |
       |public class ${name}Generic<C>: I${name}<C> where C: class, IClientTransportContext {
       |    public IClientTransport<C> Transport { get; private set; }
       |
       |    public ${name}Generic(IClientTransport<C> t) {
       |        Transport = t;
       |    }
       |
       |    public void SetHTTPTransport(string endpoint, IJsonMarshaller marshaller, bool blocking = false, int timeout = 60) {
       |        if (blocking) {
       |            this.Transport = new SyncHttpTransportGeneric<C>(endpoint, marshaller, timeout);
       |        } else {
       |            this.Transport = new AsyncHttpTransportGeneric<C>(endpoint, marshaller, timeout);
       |        }
       |    }
       |${i.methods.map(me => renderServiceClientMethod(i, me)).mkString("\n").shift(4)}
       |}
       |
       |public class ${name}: ${name}Generic<IClientTransportContext> {
       |    public ${name}(IClientTransport<IClientTransportContext> t): base(t) {}
       |}
     """.stripMargin
  }

  protected def renderServiceDispatcherHandler(i: Service, method: Service.DefMethod)(implicit imports: CSharpImports, ts: Typespace): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""case "${m.name}": {
         |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"var obj = marshaller.Unmarshal<${if (m.signature.input.fields.nonEmpty) s"${i.id.name}.In${m.name.capitalize}" else "object"}>(data);"}
         |    return marshaller.Marshal<${renderServiceMethodOutputModel(i, m)}>(\n        server.${m.name.capitalize}(ctx${if (m.signature.input.fields.isEmpty) "" else ", "}${m.signature.input.fields.map(f => s"obj.${f.name.capitalize}").mkString(", ")})\n    );
         |}
       """.stripMargin
  }

  protected def renderServiceDispatcher(i: Service)(implicit imports: CSharpImports, ts: Typespace): String = {
    val name = s"${i.id.name}Dispatcher"

    s"""public interface I${i.id.name}Server<C> {
       |${i.methods.map(m => renderServiceMethodSignature(i, m, forClient = false) + ";").mkString("\n").shift(4)}
       |}
       |
       |public class ${i.id.name}Dispatcher<C, D>: IServiceDispatcher<C, D> {
       |    private static readonly string[] methods = { ${i.methods.map(m => if (m.isInstanceOf[DefMethod.RPCMethod]) "\"" + m.asInstanceOf[DefMethod.RPCMethod].name + "\"" else "").mkString(", ")} };
       |    private IMarshaller<D> marshaller;
       |    private I${i.id.name}Server<C> server;
       |
       |    public ${i.id.name}Dispatcher(IMarshaller<D> marshaller, I${i.id.name}Server<C> server) {
       |        this.marshaller = marshaller;
       |        this.server = server;
       |    }
       |
       |    public string GetSupportedService() {
       |        return "${i.id.name}";
       |    }
       |
       |    public string[] GetSupportedMethods() {
       |        return ${i.id.name}Dispatcher<C, D>.methods;
       |    }
       |
       |    public D Dispatch(C ctx, string method, D data) {
       |        switch(method) {
       |${i.methods.map(m => renderServiceDispatcherHandler(i, m)).mkString("\n").shift(12)}
       |            default:
       |                throw new DispatcherException(string.Format("Method {0} is not supported by ${i.id.name}Dispatcher.", method));
       |        }
       |    }
       |}
     """.stripMargin
  }

  protected def renderServiceServerDummyMethod(i: Service, member: Service.DefMethod)(implicit imports: CSharpImports, ts: Typespace): String = {
    val retValue = member match {
      case m: DefMethod.RPCMethod => m.signature.output match {
        case _: Struct | _: Algebraic => "null"
        case s: Singular => CSharpType(s.typeId).defaultValue;
      }
      case _ => throw new Exception("Unsupported renderServiceServerDummyMethod case.")
    }
    s"""public ${renderServiceMethodSignature(i, member, forClient = false)} {
       |    return $retValue;
       |}
     """.stripMargin
  }

  protected def renderServiceServerDummy(i: Service)(implicit imports: CSharpImports, ts: Typespace): String = {
    val name = s"${i.id.name}ServerDummy"
    s"""public class $name<C>: I${i.id.name}Server<C> {
       |${i.methods.map(m => renderServiceServerDummyMethod(i, m)).mkString("\n").shift(4)}
       |}
     """.stripMargin
  }

  protected def renderServiceMethodOutModel(i: Service, name: String, out: Service.DefMethod.Output)(implicit imports: CSharpImports, ts: Typespace): String = out match {
    case st: Struct => renderServiceMethodInModel(i, name, st.struct)
    case al: Algebraic => renderAdtImpl(name, al.alternatives, renderUsings = false)
    case si: Singular => s"// ${si.typeId}"
  }

  protected def renderServiceMethodInModel(i: Service, name: String, structure: SimpleStructure)(implicit imports: CSharpImports, ts: Typespace): String = {
    val csClass = CSharpClass(DTOId(i.id, name), structure)

    s"""${ext.preModelEmit(ctx, csClass.id.name, csClass)}
       |${csClass.render(withWrapper = true, withSlices = false, withRTTI = true)}
       |${ext.postModelEmit(ctx, csClass.id.name, csClass)}""".stripMargin
  }

  protected def renderServiceMethodModels(i: Service, method: Service.DefMethod)(implicit imports: CSharpImports, ts: Typespace): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""${if (m.signature.input.fields.isEmpty) "" else renderServiceMethodInModel(i, s"In${m.name.capitalize}", m.signature.input)}
         |${renderServiceMethodOutModel(i, s"Out${m.name.capitalize}", m.signature.output)}
       """.stripMargin

  }

  protected def renderServiceMethodAdtUsings(i: Service, method: Service.DefMethod)(implicit imports: CSharpImports, ts: Typespace): List[String] = method match {
    case m: DefMethod.RPCMethod => m.signature.output match {
      case al: Algebraic => al.alternatives.map(adtm => renderAdtUsings(adtm))
      case _ => List.empty
    }
  }

  protected def renderServiceModels(i: Service)(implicit imports: CSharpImports, ts: Typespace): String = {
    i.methods.map(me => renderServiceMethodModels(i, me)).mkString("\n")
  }

  protected def renderServiceUsings(i: Service)(implicit imports: CSharpImports, ts: Typespace): String = {
    i.methods.flatMap(me => renderServiceMethodAdtUsings(i, me)).distinct.mkString("\n")
  }

  protected def renderService(i: Service)(implicit im: CSharpImports, ts: Typespace): RenderableCogenProduct = {
    val svc =
      s"""${renderServiceUsings(i)}
         |
         |public static class ${i.id.name} {
         |${renderServiceModels(i).shift(4)}
         |}
         |
         |// ============== Service Client ==============
         |${renderServiceClient(i)}
         |
         |// ============== Service Dispatcher ==============
         |${renderServiceDispatcher(i)}
         |
         |// ============== Service Server Dummy ==============
         |${renderServiceServerDummy(i)}
         """.stripMargin

    val extraImports = i.methods.flatMap(me => me match {
      case m: DefMethod.RPCMethod =>

        val sigTypes = m.signature.output match {
          case al: Algebraic => al.alternatives.map(_.typeId)
          case si: Singular => Seq(si.typeId)
          case st: Struct => st.struct.fields.map(_.typeId) ++ st.struct.concepts
        }

        (sigTypes.filterNot(_.isInstanceOf[Builtin]).map(typespace.apply)
          ++ Seq(Adt(AdtId(TypePath(DomainId.Undefined, Seq.empty), "FakeName"), List.empty)) // TODO:fake entry to trigger imports (unsafe)
          ).flatMap(defn => ext.imports(ctx, defn))

    })

    ServiceProduct(svc, im.renderImports(List("IRT", "System", "System.Collections", "System.Collections.Generic") ++ extraImports)) //imports.renderImports(List("irt")))
  }
}


