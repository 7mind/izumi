package izumi.idealingua.translator.tocsharp

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common.{Builtin, DomainId, TypeId, TypePath}
import izumi.idealingua.model.il.ast.typed.DefMethod.Output.{Algebraic, Alternative, Singular, Struct, Void}
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.il.ast.typed.{DefMethod, _}
import izumi.idealingua.model.output.Module
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.CompilerOptions._
import izumi.idealingua.translator.tocsharp.extensions.{JsonNetExtension, NUnitExtension}
import izumi.idealingua.translator.tocsharp.products.CogenProduct._
import izumi.idealingua.translator.tocsharp.products.RenderableCogenProduct
import izumi.idealingua.translator.tocsharp.types.{CSharpClass, CSharpField, CSharpType}
import izumi.idealingua.translator.{Translated, Translator}

object CSharpTranslator {
  final val defaultExtensions = Seq(
    JsonNetExtension,
    NUnitExtension
  )
}


class CSharpTranslator(ts: Typespace, options: CSharpTranslatorOptions) extends Translator {
  protected val ctx: CSTContext = new CSTContext(ts, options.extensions)

  import ctx._

  def translate(): Translated = {
    val modules = Seq(
      typespace.domain.types.flatMap(translateDef)
      , typespace.domain.services.flatMap(translateService)
      , typespace.domain.buzzers.flatMap(translateBuzzer)
    ).flatten

    Translated(ts, modules)
  }


  protected def translateService(definition: Service): Seq[Module] = {
    implicit val ts: Typespace = this.ts
    implicit val imports: CSharpImports = CSharpImports(definition, definition.id.domain.toPackage, List.empty)
    ctx.modules.toSource(definition.id.domain, ctx.modules.toModuleId(definition.id), renderService(definition))
  }

  protected def translateBuzzer(definition: Buzzer): Seq[Module] = {
    implicit val ts: Typespace = this.ts
    implicit val imports: CSharpImports = CSharpImports(definition, definition.id.domain.toPackage, List.empty)
    ctx.modules.toSource(definition.id.domain, ctx.modules.toModuleId(definition.id), renderBuzzer(definition))
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
         |// using ${i.id.name} = ${cstype.renderType(true)}
         |//
         |// For the time being, please use the target type everywhere you need.
         """.stripMargin
    )
  }

  protected def renderAdtMember(adtName: String, member: AdtMember): String = {
    val operators =
      s"""    public static explicit operator _${member.wireId}(${member.wireId} m) {
         |        return m.Value;
         |    }
         |
         |    public static explicit operator ${member.wireId}(_${member.wireId} m) {
         |        return new ${member.wireId}(m);
         |    }
       """.stripMargin

    val operatorsDummy =
      s"""    // We would normally want to have an operator, but unfortunately if it is an interface,
         |    // it will fail on "user-defined conversions to or from an interface are not allowed".
         |    // public static explicit operator _${member.wireId}(${member.wireId} m) {
         |    //     return m.Value;
         |    // }
         |    //
         |    // public static explicit operator ${member.wireId}(_${member.wireId} m) {
         |    //     return new ${member.wireId}(m);
         |    // }
       """.stripMargin

    //    val memberType = CSharpType(member.typeId)
    s"""public sealed class ${member.wireId}: $adtName {
       |    public _${member.wireId} Value { get; private set; }
       |    public ${member.wireId}(_${member.wireId} value) {
       |        this.Value = value;
       |    }
       |
       |    public override void Visit(I${adtName}Visitor visitor) {
       |        visitor.Visit(this);
       |    }
       |
       |${if (member.typeId.isInstanceOf[InterfaceId]) operatorsDummy else operators}
       |}
     """.stripMargin
  }

  protected def renderAdtUsings(m: AdtMember)(implicit im: CSharpImports, ts: Typespace): String = {
    s"using _${m.wireId} = ${CSharpType(m.typeId).renderType(true)};"
  }

  protected def renderAdtImpl(adtName: String, members: List[AdtMember], renderUsings: Boolean = true)(implicit im: CSharpImports, ts: Typespace): String = {
    val adt = Adt(AdtId(TypePath(DomainId.Undefined, Seq.empty), adtName), members, NodeMeta.empty)
    s"""${im.renderUsings()}
       |${if (renderUsings) members.map(m => renderAdtUsings(m)).mkString("\n") else ""}
       |
       |${ext.preModelEmit(ctx, adt)}
       |public abstract class $adtName {
       |    public interface I${adtName}Visitor {
       |${members.map(m => s"        void Visit(${m.wireId} visitor);").mkString("\n")}
       |    }
       |
       |    public abstract void Visit(I${adtName}Visitor visitor);
       |    private $adtName() {}
       |
       |${members.map(m => renderAdtMember(adtName, m)).mkString("\n").shift(4)}
       |}
       |${ext.postModelEmit(ctx, adt)}
     """.stripMargin
  }

  protected def renderServiceMethodAlternativeOutput(name: String, at: Alternative, success: Boolean)(implicit im: CSharpImports, ts: Typespace): String = {
    if (success)
      at.success match {
        case _: Algebraic => s"${name}Success" /*ts.tools.toNegativeBranchName(alternative.failure.)*/
        case _: Struct => s"${name}Success"
        case si: Singular => CSharpType(si.typeId).renderType(true)
        case _ => throw new Exception("Not supported alternative non singular or algebraic " + at.success.toString)
      }
    else
      at.failure match {
        case _: Algebraic => s"${name}Failure" /*ts.tools.toNegativeBranchName(alternative.failure.)*/
        case _: Struct => s"${name}Failure"
        case si: Singular => CSharpType(si.typeId).renderType(true)
        case _ => throw new Exception("Not supported alternative non singular or algebraic " + at.failure.toString)
      }
  }

  protected def renderServiceMethodAlternativeOutputTypeId(typePath: TypePath, name: String, at: Alternative, success: Boolean): TypeId = {
    if (success)
      at.success match {
        case _: Algebraic => new AdtId(TypePath(typePath.domain, Seq(s"${name}Success")), s"${name}Success")
        case _: Struct => new DTOId(TypePath(typePath.domain, Seq(s"${name}Success")), s"${name}Success")
        case si: Singular => si.typeId
        case _ => throw new Exception("Not supported alternative non singular or algebraic " + at.success.toString)
      }
    else
      at.failure match {
        case _: Algebraic => new AdtId(TypePath(typePath.domain, Seq(s"${name}Failure")), s"${name}Failure")
        case _: Struct => new DTOId(TypePath(typePath.domain, Seq(s"${name}Failure")), s"${name}Failure")
        case si: Singular => si.typeId
        case _ => throw new Exception("Not supported alternative non singular or algebraic " + at.failure.toString)
      }
  }

  protected def renderAlternativeType(name: String, alternative: Alternative)(implicit im: CSharpImports, ts: Typespace): String = {
    val leftType = renderServiceMethodAlternativeOutput(name, alternative, success = false)
    val rightType = renderServiceMethodAlternativeOutput(name, alternative, success = true)

    s"Either<$leftType, $rightType> "
  }

  protected def renderAlternativeImpl(structId: DTOId, name: String, alternative: Alternative)(implicit im: CSharpImports, ts: Typespace): String = {
    // val leftType = renderServiceMethodAlternativeOutput(name, alternative, success = false)
    // val leftTypeId = renderServiceMethodAlternativeOutputTypeId(structId.path, name, alternative, success = false)
    val left = alternative.failure match {
      case al: Algebraic => renderAdtImpl(renderServiceMethodAlternativeOutput(name, alternative, success = false), al.alternatives, renderUsings = false)
      case st: Struct => renderServiceMethodInModel(new DTOId(structId.path, structId.name + "Failure"), st.struct)
      case _ => ""
    }

    // val rightType = renderServiceMethodAlternativeOutput(name, alternative, success = true)
    // val rightTypeId = renderServiceMethodAlternativeOutputTypeId(structId.path, name, alternative, success = true)
    val right = alternative.success match {
      case al: Algebraic => renderAdtImpl(renderServiceMethodAlternativeOutput(name, alternative, success = true), al.alternatives, renderUsings = false)
      case st: Struct => renderServiceMethodInModel(new DTOId(structId.path, structId.name + "Success"), st.struct)
      case _ => ""
    }

    /* // This is last resort here where we would need to actually create an instance of a class
    s"""$left
       |$right
       |${ext.preModelEmit(ctx, name, alternative)}
       |type public abstract class $name: Either<$leftType, $rightType> {
       |}
       |${ext.postModelEmit(ctx, name, alternative, leftTypeId, rightTypeId)}
     """.stripMargin
    */
    s"""$left
       |$right
     """.stripMargin
  }

  protected def renderAdt(i: Adt)(implicit im: CSharpImports, ts: Typespace): RenderableCogenProduct = {

    AdtProduct(renderAdtImpl(i.id.name, i.alternatives), im.renderImports(ext.imports(ctx, i).toList))
  }

  protected def renderEnumeration(i: Enumeration)(implicit im: CSharpImports, ts: Typespace): RenderableCogenProduct = {
    val name = i.id.name
    // Alternative way using reflection for From method:
    // return ($name)Enum.Parse(typeof($name), value);
    val decl =
    s"""// $name Enumeration
       |public enum $name {
       |${i.members.map(_.value).map(m => s"$m${if (m == i.members.last.value) "" else ","}").mkString("\n").shift(4)}
       |}
       |
         |public static class ${name}Helpers {
       |    public static $name From(string value) {
       |        switch (value) {
       |${i.members.map(_.value).map(m => s"""case \"$m\": return $name.$m;""").mkString("\n").shift(12)}
       |            default:
       |                throw new ArgumentOutOfRangeException();
       |        }
       |    }
       |
         |    public static bool IsValid(string value) {
       |        return Enum.IsDefined(typeof($name), value);
       |    }
       |
         |    // The elements in the array are still changeable, please use with care.
       |    private static readonly $name[] all = new $name[] {
       |${i.members.map(_.value).map(m => s"$name.$m${if (m == i.members.last.value) "" else ","}").mkString("\n").shift(8)}
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
         |        if (!value.StartsWith("${i.id.name}#", StringComparison.Ordinal)) {
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
      (f.defn.variance.nonEmpty, CSharpField(/*if (f.defn.variance.nonEmpty) f.defn.variance.last else */ f.field, eid.name, Seq.empty)))

    val struct = CSharpClass(eid, i.id.name + eid.name, structure, List(i.id))
    val ifaceImplements = if (i.struct.superclasses.interfaces.isEmpty) ": IRTTI" else ": " +
      i.struct.superclasses.interfaces.map(ifc => ifc.name).mkString(", ") + ", IRTTI"
    val dto = DTO(eid, Structure(validFields.map(f => f.field), List.empty, Super(List(i.id), List.empty, List.empty)), NodeMeta.empty)

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

    InterfaceProduct(iface, companion, im.renderImports(List("IRT", "System", "System.Collections", "System.Collections.Generic", "System.Reflection") ++ ext.imports(ctx, i).toList))
  }

  protected def isServiceMethodReturnExistent(method: DefMethod.RPCMethod): Boolean = method.signature.output match {
    case _: Void => false
    case _ => true
  }

  protected def renderRPCMethodSignature(svcOrBuzzer: String, method: DefMethod, forClient: Boolean)
                                        (implicit imports: CSharpImports, ts: Typespace): String = {
    method match {
      case m: DefMethod.RPCMethod => {
        val returnValue = if (isServiceMethodReturnExistent(method.asInstanceOf[DefMethod.RPCMethod])) s"<${renderRPCMethodOutputSignature(svcOrBuzzer, m)}>" else ""

        val callback = s"${if (m.signature.input.fields.isEmpty) "" else ", "}Action$returnValue onSuccess, Action<Exception> onFailure, Action onAny = null, C ctx = null"
        val fields = m.signature.input.fields.map(f => CSharpType(f.typeId).renderType(true) + " " + CSharpField.safeVarName(f.name)).mkString(", ")
        val context = s"C ctx${if (m.signature.input.fields.isEmpty) "" else ", "}"
        if (forClient) {
          s"void ${m.name.capitalize}($fields$callback)"
        } else {
          s"${renderRPCMethodOutputSignature(svcOrBuzzer, m)} ${m.name.capitalize}($context$fields)"
        }
      }
    }
  }

  protected def renderRPCMethodOutputModel(svcOrBuzzer: String, method: DefMethod.RPCMethod)(implicit imports: CSharpImports, ts: Typespace): String = method.signature.output match {
    case _: Struct => s"$svcOrBuzzer.Out${method.name.capitalize}"
    case _: Algebraic => s"$svcOrBuzzer.Out${method.name.capitalize}"
    case si: Singular => s"${CSharpType(si.typeId).renderType(true)}"
    case _: Void => "void"
    case at: Alternative => renderAlternativeType(s"$svcOrBuzzer.Out${method.name.capitalize}", at)
  }

  protected def renderRPCMethodOutputSignature(svcOrBuzzer: String, method: DefMethod.RPCMethod)(implicit imports: CSharpImports, ts: Typespace): String = {
    s"${renderRPCMethodOutputModel(svcOrBuzzer, method)}"
  }

  protected def renderRPCClientMethod(svcOrBuzzer: String, method: DefMethod)(implicit imports: CSharpImports, ts: Typespace): String = method match {
    case m: DefMethod.RPCMethod => m.signature.output match {
      case _: Struct | _: Algebraic | _: Alternative =>
        s"""public ${renderRPCMethodSignature(svcOrBuzzer, method, forClient = true)} {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"var inData = new $svcOrBuzzer.In${m.name.capitalize}(${m.signature.input.fields.map(ff => CSharpField.safeVarName(ff.name)).mkString(", ")});"}
           |    Transport.Send<${if (m.signature.input.fields.nonEmpty) s"$svcOrBuzzer.In${m.name.capitalize}" else "object"}, ${renderRPCMethodOutputModel(svcOrBuzzer, m)}>("$svcOrBuzzer", "${m.name}", ${if (m.signature.input.fields.isEmpty) "null" else "inData"},
           |        new ClientTransportCallback<${renderRPCMethodOutputModel(svcOrBuzzer, m)}>(onSuccess, onFailure, onAny), ctx);
           |}
       """.stripMargin

      case _: Singular =>
        s"""public ${renderRPCMethodSignature(svcOrBuzzer, method, forClient = true)} {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"var inData = new $svcOrBuzzer.In${m.name.capitalize}(${m.signature.input.fields.map(ff => CSharpField.safeVarName(ff.name)).mkString(", ")});"}
           |    Transport.Send<${if (m.signature.input.fields.nonEmpty) s"$svcOrBuzzer.In${m.name.capitalize}" else "object"}, ${renderRPCMethodOutputModel(svcOrBuzzer, m)}>("$svcOrBuzzer", "${m.name}", ${if (m.signature.input.fields.isEmpty) "null" else "inData"},
           |        new ClientTransportCallback<${renderRPCMethodOutputModel(svcOrBuzzer, m)}>(onSuccess, onFailure, onAny), ctx);
           |}
       """.stripMargin

      case _: Void =>
        s"""public ${renderRPCMethodSignature(svcOrBuzzer, method, forClient = true)} {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"var inData = new $svcOrBuzzer.In${m.name.capitalize}(${m.signature.input.fields.map(ff => CSharpField.safeVarName(ff.name)).mkString(", ")});"}
           |    Transport.Send<${if (m.signature.input.fields.nonEmpty) s"$svcOrBuzzer.In${m.name.capitalize}" else "object"}, IRT.Void>("$svcOrBuzzer", "${m.name}", ${if (m.signature.input.fields.isEmpty) "null" else "inData"},
           |        new ClientTransportCallback<IRT.Void>(_ => onSuccess(), onFailure, onAny), ctx);
           |}
       """.stripMargin
    }
  }

  protected def renderServiceClient(i: Service)(implicit imports: CSharpImports, ts: Typespace): String = {
    val name = s"${i.id.name}Client"

    s"""public interface I$name<C> where C: class, IClientTransportContext {
       |${i.methods.map(m => renderRPCMethodSignature(i.id.name, m, forClient = true) + ";").mkString("\n").shift(4)}
       |}
       |
       |public class ${name}Generic<C>: I$name<C> where C: class, IClientTransportContext {
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
       |${i.methods.map(me => renderRPCClientMethod(i.id.name, me)).mkString("\n").shift(4)}
       |}
       |
       |public class $name: ${name}Generic<IClientTransportContext> {
       |    public $name(IClientTransport<IClientTransportContext> t): base(t) {}
       |}
     """.stripMargin
  }

  protected def renderRPCDispatcherHandler(svcOrBuzzer: String, method: DefMethod, server: String)(implicit imports: CSharpImports, ts: Typespace): String = method match {
    case m: DefMethod.RPCMethod =>
      if (isServiceMethodReturnExistent(m))
        s"""case "${m.name}": {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"var obj = marshaller.Unmarshal<${if (m.signature.input.fields.nonEmpty) s"$svcOrBuzzer.In${m.name.capitalize}" else "object"}>(data);"}
           |    return marshaller.Marshal<${renderRPCMethodOutputModel(svcOrBuzzer, m)}>(\n        $server.${m.name.capitalize}(ctx${if (m.signature.input.fields.isEmpty) "" else ", "}${m.signature.input.fields.map(f => s"obj.${f.name.capitalize}").mkString(", ")})\n    );
           |}
         """.stripMargin
      else
        s"""case "${m.name}": {
           |    ${if (m.signature.input.fields.isEmpty) "// No input params for this method" else s"var obj = marshaller.Unmarshal<${if (m.signature.input.fields.nonEmpty) s"$svcOrBuzzer.In${m.name.capitalize}" else "object"}>(data);"}
           |    $server.${m.name.capitalize}(ctx${if (m.signature.input.fields.isEmpty) "" else ", "}${m.signature.input.fields.map(f => s"obj.${f.name.capitalize}").mkString(", ")});
           |    return marshaller.Marshal<IRT.Void>(null);
           |}
       """.stripMargin
  }

  protected def renderServiceDispatcher(i: Service)(implicit imports: CSharpImports, ts: Typespace): String = {
    s"""public interface I${i.id.name}Server<C> {
       |${i.methods.map(m => renderRPCMethodSignature(i.id.name, m, forClient = false) + ";").mkString("\n").shift(4)}
       |}
       |
       |public class ${i.id.name}Dispatcher<C, D>: IServiceDispatcher<C, D> {
       |    private static readonly string[] methods = { ${i.methods.map(m => if (m.isInstanceOf[DefMethod.RPCMethod]) "\"" + m.asInstanceOf[DefMethod.RPCMethod].name + "\"" else "").mkString(", ")} };
       |    protected IMarshaller<D> marshaller;
       |    protected I${i.id.name}Server<C> server;
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
       |${i.methods.map(m => renderRPCDispatcherHandler(i.id.name, m, "server")).mkString("\n").shift(12)}
       |            default:
       |                throw new DispatcherException(string.Format("Method {0} is not supported by ${i.id.name}Dispatcher.", method));
       |        }
       |    }
       |}
     """.stripMargin
  }

  protected def renderRPCDummyMethod(svcOrBuzzer: String, member: DefMethod, virtual: Boolean)(implicit imports: CSharpImports, ts: Typespace): String = {
    val retValue = member match {
      case m: DefMethod.RPCMethod => m.signature.output match {
        case _: Struct | _: Algebraic | _: Alternative => "return null;"
        case s: Singular => "return " + CSharpType(s.typeId).defaultValue + ";";
        case _: Void => "// Nothing to return"
      }
      case _ => throw new Exception("Unsupported renderServiceServerDummyMethod case.")
    }
    s"""public ${if (virtual) "virtual " else ""}${renderRPCMethodSignature(svcOrBuzzer, member, forClient = false)} {
       |    $retValue
       |}
     """.stripMargin
  }

  protected def renderServiceServerBase(i: Service)(implicit imports: CSharpImports, ts: Typespace): String = {
    val name = s"${i.id.name}Server"
    s"""public abstract class $name<C, D>: ${i.id.name}Dispatcher<C, D>,  I${i.id.name}Server<C> {
       |    public $name(IMarshaller<D> marshaller): base(marshaller, null) {
       |        server = this;
       |    }
       |
       |${i.methods.map(m => renderRPCDummyMethod(i.id.name, m, virtual = true)).mkString("\n").shift(4)}
       |}
     """.stripMargin
  }

  protected def outputToAdtMember(out: DefMethod.Output): List[AdtMember] = out match {
    case si: Singular => List(AdtMember(si.typeId, None, NodeMeta.empty))
    case al: Algebraic => al.alternatives
    case _: Struct => List.empty
    case _ => throw new Exception("Output type to TypeId is not supported for non singular or void types. " + out)
  }

  protected def renderServiceMethodOutModel(i: Service, name: String, out: DefMethod.Output)(implicit imports: CSharpImports, ts: Typespace): String = out match {
    case st: Struct => renderServiceMethodInModel(i, name, st.struct)
    case al: Algebraic => renderAdtImpl(name, al.alternatives, renderUsings = false)
    case si: Singular => s"// ${si.typeId}"
    case _: Void => ""
    case at: Alternative => renderAlternativeImpl(DTOId(i.id, name), name, at)
  }

  protected def renderBuzzerMethodOutModel(i: Buzzer, name: String, out: DefMethod.Output)(implicit imports: CSharpImports, ts: Typespace): String = out match {
    case st: Struct => renderBuzzerMethodInModel(i, name, st.struct)
    case al: Algebraic => renderAdtImpl(name, al.alternatives, renderUsings = false)
    case si: Singular => s"// ${si.typeId}"
    case _: Void => ""
    case at: Alternative => renderAlternativeImpl(DTOId(i.id, name), name, at)
  }

  protected def renderServiceMethodInModel(i: Service, name: String, structure: SimpleStructure)(implicit imports: CSharpImports, ts: Typespace): String = {
    renderServiceMethodInModel(DTOId(i.id, name), structure)
  }

  protected def renderBuzzerMethodInModel(i: Buzzer, name: String, structure: SimpleStructure)(implicit imports: CSharpImports, ts: Typespace): String = {
    renderServiceMethodInModel(DTOId(i.id, name), structure)
  }

  protected def renderServiceMethodInModel(i: DTOId, structure: SimpleStructure)(implicit imports: CSharpImports, ts: Typespace): String = {
    val csClass = CSharpClass(i, structure)

    s"""${ext.preModelEmit(ctx, csClass.id.name, csClass)}
       |${csClass.render(withWrapper = true, withSlices = false, withRTTI = true)}
       |${ext.postModelEmit(ctx, csClass.id.name, csClass)}""".stripMargin
  }

  protected def renderServiceMethodModels(i: Service, method: DefMethod)(implicit imports: CSharpImports, ts: Typespace): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""${if (m.signature.input.fields.isEmpty) "" else renderServiceMethodInModel(i, s"In${m.name.capitalize}", m.signature.input)}
         |${renderServiceMethodOutModel(i, s"Out${m.name.capitalize}", m.signature.output)}
       """.stripMargin

  }

  protected def renderServiceMethodAdtUsings(method: DefMethod)(implicit imports: CSharpImports, ts: Typespace): List[String] = method match {
    case m: DefMethod.RPCMethod => m.signature.output match {
      case al: Algebraic => al.alternatives.map(adtm => renderAdtUsings(adtm))
//      case at: Alternative => (outputToAdtMember(at.failure) ++ outputToAdtMember(at.success)).map(adtm => renderAdtUsings(adtm))
      case _ => List.empty
    }
  }

  protected def renderServiceModels(i: Service)(implicit imports: CSharpImports, ts: Typespace): String = {
    i.methods.map(me => renderServiceMethodModels(i, me)).mkString("\n")
  }

  protected def renderServiceUsings(i: Service)(implicit imports: CSharpImports, ts: Typespace): String = {
    i.methods.flatMap(me => renderServiceMethodAdtUsings(me)).distinct.mkString("\n")
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
         |// ============== Service Server Base ==============
         |${renderServiceServerBase(i)}
         """.stripMargin

    val extraImports = i.methods.flatMap(me => me match {
      case m: DefMethod.RPCMethod =>

        val sigTypes = m.signature.output match {
          case al: Algebraic => al.alternatives.map(_.typeId)
          case si: Singular => Seq(si.typeId)
          case st: Struct => st.struct.fields.map(_.typeId) ++ st.struct.concepts
          case _: Void => Seq.empty
          case at: Alternative => (outputToAdtMember(at.success) ++ outputToAdtMember(at.failure)).map(am => am.typeId)
        }

        (sigTypes.filterNot(_.isInstanceOf[Builtin]).map(typespace.apply)
          ++ Seq(Adt(AdtId(TypePath(DomainId.Undefined, Seq.empty), "FakeName"), List.empty, NodeMeta.empty)) // TODO:fake entry to trigger imports (unsafe)
          ).flatMap(defn => ext.imports(ctx, defn))

    })

    ServiceProduct(svc, im.renderImports(List("IRT", "IRT.Marshaller", "IRT.Transport.Client", "System", "System.Collections", "System.Collections.Generic") ++ extraImports))
  }

  protected def renderBuzzerUsings(i: Buzzer)(implicit imports: CSharpImports, ts: Typespace): String = {
    i.events.flatMap(me => renderServiceMethodAdtUsings(me)).distinct.mkString("\n")
  }

  protected def renderBuzzerModels(i: Buzzer)(implicit imports: CSharpImports, ts: Typespace): String = {
    i.events.map(me => renderBuzzerMethodModels(i, me)).mkString("\n")
  }

  protected def renderBuzzerMethodModels(i: Buzzer, method: DefMethod)(implicit imports: CSharpImports, ts: Typespace): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""${if (m.signature.input.fields.isEmpty) "" else renderBuzzerMethodInModel(i, s"In${m.name.capitalize}", m.signature.input)}
         |${renderBuzzerMethodOutModel(i, s"Out${m.name.capitalize}", m.signature.output)}
       """.stripMargin
  }

  protected def renderBuzzerClient(i: Buzzer)(implicit imports: CSharpImports, ts: Typespace): String = {
    val name = s"${i.id.name}Client"

    s"""public interface I$name<C> where C: class, IClientTransportContext {
       |${i.events.map(m => renderRPCMethodSignature(i.id.name, m, forClient = true) + ";").mkString("\n").shift(4)}
       |}
       |
       |public class ${name}Generic<C, D>: I$name<C> where C: class, IClientTransportContext {
       |    public IClientSocketTransport<C, D> Transport { get; private set; }
       |
       |    public ${name}Generic(IClientSocketTransport<C, D> t) {
       |        Transport = t;
       |    }
       |
       |${i.events.map(me => renderRPCClientMethod(i.id.name, me)).mkString("\n").shift(4)}
       |}
       |
       |public class $name: ${name}Generic<IClientTransportContext, string> {
       |    public $name(IClientSocketTransport<IClientTransportContext, string> t): base(t) {}
       |}
     """.stripMargin
  }

  protected def renderBuzzerDispatcher(i: Buzzer)(implicit imports: CSharpImports, ts: Typespace): String = {
    s"""public interface I${i.id.name}BuzzerHandlers<C> {
       |${i.events.map(m => renderRPCMethodSignature(i.id.name, m, forClient = false) + ";").mkString("\n").shift(4)}
       |}
       |
       |public class ${i.id.name}Dispatcher<C, D>: IServiceDispatcher<C, D> {
       |    private static readonly string[] methods = { ${i.events.map(m => if (m.isInstanceOf[DefMethod.RPCMethod]) "\"" + m.asInstanceOf[DefMethod.RPCMethod].name + "\"" else "").mkString(", ")} };
       |    protected IMarshaller<D> marshaller;
       |    protected I${i.id.name}BuzzerHandlers<C> handlers;
       |
       |    public ${i.id.name}Dispatcher(IMarshaller<D> marshaller, I${i.id.name}BuzzerHandlers<C> handlers) {
       |        this.marshaller = marshaller;
       |        this.handlers = handlers;
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
       |${i.events.map(m => renderRPCDispatcherHandler(i.id.name, m, "handlers")).mkString("\n").shift(12)}
       |            default:
       |                throw new DispatcherException(string.Format("Method {0} is not supported by ${i.id.name}Dispatcher.", method));
       |        }
       |    }
       |}
     """.stripMargin
  }

  protected def renderBuzzerHandlersDummy(i: Buzzer)(implicit imports: CSharpImports, ts: Typespace): String = {
    val name = s"${i.id.name}BuzzerHandlers"
    s"""public abstract class $name<C, D>: ${i.id.name}Dispatcher<C, D>,  I${i.id.name}BuzzerHandlers<C> {
       |    public $name(IMarshaller<D> marshaller): base(marshaller, null) {
       |        handlers = this;
       |    }
       |
       |${i.events.map(m => renderRPCDummyMethod(i.id.name, m, virtual = true)).mkString("\n").shift(4)}
       |}
     """.stripMargin
  }

  protected def renderBuzzer(i: Buzzer)(implicit im: CSharpImports, ts: Typespace): RenderableCogenProduct = {
    val svc =
      s"""${renderBuzzerUsings(i)}
         |
         |public static class ${i.id.name} {
         |${renderBuzzerModels(i).shift(4)}
         |}
         |
         |// ============== Client ==============
         |${renderBuzzerClient(i)}
         |
         |// ============== Dispatcher ==============
         |${renderBuzzerDispatcher(i)}
         |
         |// ============== Buzzer Handlers Base ==============
         |${renderBuzzerHandlersDummy(i)}
         """.stripMargin

    val extraImports = i.events.flatMap(me => me match {
      case m: DefMethod.RPCMethod =>

        val sigTypes = m.signature.output match {
          case al: Algebraic => al.alternatives.map(_.typeId)
          case si: Singular => Seq(si.typeId)
          case st: Struct => st.struct.fields.map(_.typeId) ++ st.struct.concepts
          case _: Void => Seq.empty
          case at: Alternative => (outputToAdtMember(at.success) ++ outputToAdtMember(at.failure)).map(am => am.typeId)
        }

        (sigTypes.filterNot(_.isInstanceOf[Builtin]).map(typespace.apply)
          ++ Seq(Adt(AdtId(TypePath(DomainId.Undefined, Seq.empty), "FakeName"), List.empty, NodeMeta.empty)) // TODO:fake entry to trigger imports (unsafe)
          ).flatMap(defn => ext.imports(ctx, defn))

    })

    BuzzerProduct(svc, im.renderImports(List("IRT", "IRT.Marshaller", "IRT.Transport.Client", "System", "System.Collections", "System.Collections.Generic") ++ extraImports))
  }
}
