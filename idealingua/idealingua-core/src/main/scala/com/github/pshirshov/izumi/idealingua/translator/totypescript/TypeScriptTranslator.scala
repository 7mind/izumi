package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.Output.{Algebraic, Singular, Struct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.{EnumHelpersExtension, TypeScriptTranslatorExtension}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.products.CogenProduct._
import com.github.pshirshov.izumi.idealingua.translator.totypescript.products.RenderableCogenProduct

object TypeScriptTranslator {
  final val defaultExtensions = Seq(
    EnumHelpersExtension
  )
}

class TypeScriptTranslator(ts: Typespace, extensions: Seq[TypeScriptTranslatorExtension]) {
  protected val ctx: TSTContext = new TSTContext(ts, extensions)

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

  protected def renderRuntimeNames(i: TypeId): String = {
      renderRuntimeNames(i.path.toPackage.mkString("."), i.name)
  }

  protected def renderRuntimeNames(pkg: String, name: String, holderName: String = null): String = {
    s"""// Runtime identification methods
       |public static readonly PackageName = ${"\"" + pkg + "\""};
       |public static readonly ClassName = ${"\"" + name + "\""};
       |public static readonly FullClassName = $name.PackageName + "." + $name.ClassName;
       |
       |public getPackageName(): string { return ${if(holderName == null) name else holderName}.PackageName; }
       |public getClassName(): string { return ${if(holderName == null) name else holderName}.ClassName; }
       |public getFullClassName(): string { return ${if(holderName == null) name else holderName}.FullClassName; }
       """.stripMargin
  }

  protected def renderDtoInterfaceSerializer(iid: InterfaceId): String = {
    val fields = typespace.structure.structure(iid)
    s"""public to${iid.name}(): ${typespace.implId(iid).name}Serialized {
       |    return {
       |${renderSerializedObject(fields.all.map(_.field)).shift(8)}
       |    };
       |}
     """.stripMargin
  }

  protected def renderDto(i: DTO): RenderableCogenProduct = {
//    val dtoIfaces = i.struct.superclasses.interfaces.map(si => {
//      val fields = typespace.structure.structure(si)
//      s"""export interface ${si.name} {
//         |${fields.all.map(f => s"${conv.toNativeTypeName(conv.safeName(f.field.name), f.field.typeId)}: ${conv.toNativeType(f.field.typeId)};").mkString("\n").shift(4)}
//         |}
//         """.stripMargin
//    }).mkString("\n")

    val fields = typespace.structure.structure(i).all
    val distinctFields = fields.groupBy(_.field.name).map(_._2.head.field)

    val importHeader = typesToImports(
      i.struct.fields.flatMap(i => collectCustomTypes(i.typeId)) ++
        i.struct.superclasses.interfaces, i.id.path.toPackage)

    val implementsInterfaces =
      if (i.struct.superclasses.interfaces.nonEmpty) {
        "implements " + i.struct.superclasses.interfaces.map(iface => iface.name).mkString(", ") + " "
      } else {
        ""
      }

    val extendsInterfacesSerialized =
      if (i.struct.superclasses.interfaces.nonEmpty) {
        "extends " + i.struct.superclasses.interfaces.map(iface => s"${typespace.implId(iface).name}Serialized").mkString(", ") + " "
      } else {
        ""
      }

    val dto =
      s"""export class ${i.id.name} $implementsInterfaces {
         |${renderRuntimeNames(i.id).shift(4)}
         |${distinctFields.map(f => conv.toFieldMember(f)).mkString("\n").shift(4)}
         |
         |${distinctFields.map(f => conv.toFieldMethods(f)).mkString("\n").shift(4)}
         |    constructor(data: ${i.id.name}Serialized = undefined) {
         |        if (typeof data === 'undefined' || data === null) {
         |            return;
         |        }
         |
         |${distinctFields.map(f => s"${conv.deserializeName("this." + f.name, f.typeId)} = ${conv.deserializeType("data." + f.name, f.typeId, typespace)};").mkString("\n").shift(8)}
         |    }
         |
         |${i.struct.superclasses.interfaces.map(si => renderDtoInterfaceSerializer(si)).mkString("\n").shift(4)}
         |    public serialize(): ${i.id.name}Serialized {
         |        return {
         |${renderSerializedObject(distinctFields.toList).shift(12)}
         |        };
         |    }
         |}
         |
         |export interface ${i.id.name}Serialized $extendsInterfacesSerialized {
         |${distinctFields.map(f => s"${conv.toNativeTypeName(f.name, f.typeId)}: ${conv.toNativeType(f.typeId, forSerialized = true, typespace)};").mkString("\n").shift(4)}
         |}
         |
         |${i.struct.superclasses.interfaces.map(sc => typespace.implId(sc).name + s".register(${i.id.name}.FullClassName, ${i.id.name});").mkString("\n")}
         """.stripMargin

    CompositeProduct(dto, importHeader, s"// ${i.id.name} DTO")
  }

  protected def renderAlias(i: Alias): RenderableCogenProduct = {
    // TODO Finish import
      val importHeader = typesToImports(Seq(i.target), i.id.path.toPackage)

      AliasProduct(
        s"export type ${i.id.name} = ${conv.toNativeType(i.target)};",
        importHeader,
        s"// ${i.id.name} alias"
      )
  }

  protected def renderAdt(i: Adt): RenderableCogenProduct = {
    val base =
      s"""type ${i.id.name} = ${i.alternatives.map(alt => alt.typeId.name).mkString(" | ")};
         |
         |export class ${i.id.name}Helpers {
         |    public static serialize(adt: ${i.id.name}): {[key: string]: ${i.alternatives.map(alt => (if (alt.typeId.isInstanceOf[InterfaceId]) typespace.implId(alt.typeId.asInstanceOf[InterfaceId]).name else alt.typeId.name) + "Serialized").mkString(" | ")}} {
         |        var className = adt.getClassName();
         |${i.alternatives.filter(al => al.memberName.isDefined).map(a => s"if (className == '${a.typeId.name}') {\n    className = '${a.memberName.get}'\n}").mkString("\n").shift(8)}
         |        return {
         |            [className]: adt.serialize()
         |        };
         |    }
         |
         |    public static deserialize(data: {[key: string]: ${i.alternatives.map(alt => (if (alt.typeId.isInstanceOf[InterfaceId]) typespace.implId(alt.typeId.asInstanceOf[InterfaceId]).name else alt.typeId.name) + "Serialized").mkString(" | ")}}): ${i.id.name} {
         |        const id = Object.keys(data)[0];
         |        const content = data[id];
         |        switch (id) {
         |${i.alternatives.map(a => "case '" + (if (a.memberName.isEmpty) a.typeId.name else a.memberName.get) + "': return " + conv.deserializeType("content", a.typeId, typespace, asAny = true) + ";").mkString("\n").shift(12)}
         |            default:
         |                throw new Error('Unknown type id ' + id + ' for ${i.id.name}');
         |        }
         |    }
         |}
       """.stripMargin

    val importHeader = typesToImports(i.alternatives.map(ii => ii.typeId), i.id.path.toPackage)

    AdtProduct(
      base,
      importHeader,
      s"// ${i.id.name} Algebraic Data Type"
    )
  }

  protected def renderEnumeration(i: Enumeration): RenderableCogenProduct = {
    val it = i.members.iterator
    val members = it.map { m =>
      s"$m = '$m'" + (if (it.hasNext) "," else "")
    }.mkString("\n")

    val content =
      s"""export enum ${i.id.name} {
         |${members.shift(4)}
         |}
       """.stripMargin

    ext.extend(i, EnumProduct(content, s"// ${i.id.name} Enumeration"), _.handleEnum)
  }

  protected def renderIdentifier(i: Identifier): RenderableCogenProduct = {
      val fields = typespace.structure.structure(i)
      val sortedFields = fields.all.sortBy(_.field.name)
      val typeName = i.id.name


      val identifierInterface =
        s"""export interface I$typeName {
           |    getPackageName(): string;
           |    getClassName(): string;
           |    getFullClassName(): string;
           |    serialize(): string;
           |
           |${fields.all.map(f => s"${conv.toNativeTypeName(conv.safeName(f.field.name), f.field.typeId)}: ${conv.toNativeType(f.field.typeId, forSerialized = true, typespace)};").mkString("\n").shift(4)}
           |}
         """.stripMargin

      val identifier =
        s"""export class $typeName implements I$typeName {
           |${renderRuntimeNames(i.id).shift(4)}
           |${fields.all.map(f => conv.toFieldMember(f.field)).mkString("\n").shift(4)}
           |
           |${fields.all.map(f => conv.toFieldMethods(f.field)).mkString("\n").shift(4)}
           |    constructor(data: string | I$typeName = undefined) {
           |        if (typeof data === 'undefined' || data === null) {
           |            return;
           |        }
           |
           |        if (typeof data === 'string') {
           |            if (!data.startsWith('$typeName#')) {
           |                throw new Error('Identifier must start with $typeName, got ' + data);
           |            }
           |            const parts = data.substr(data.indexOf('#') + 1).split(':');
           |${sortedFields.zipWithIndex.map{ case (sf, index) => s"this.${sf.field.name} = ${conv.parseTypeFromString(s"decodeURIComponent(parts[$index])", sf.field.typeId)};"}.mkString("\n").shift(12)}
           |        } else {
           |${fields.all.map(f => s"this.${f.field.name} = ${conv.deserializeType("data." + f.field.name, f.field.typeId, typespace)};").mkString("\n").shift(12)}
           |        }
           |    }
           |
           |    public toString(): string {
           |        const suffix = ${sortedFields.map(sf => "encodeURIComponent(this." + sf.field.name + ".toString())").mkString(" + ':' + ")};
           |        return '$typeName#' + suffix;
           |    }
           |
           |    public serialize(): string {
           |        return this.toString();
           |    }
           |}
         """.stripMargin

    ext.extend(i, IdentifierProduct(identifier, identifierInterface, s"", s"// ${i.id.name} Identifier"), _.handleIdentifier)
  }

  protected def collectCustomTypes(id: TypeId): Seq[TypeId] = id match {
    case _: Primitive => Seq()
    case g: Generic => g match {
      case gm: Generic.TMap => collectCustomTypes(gm.valueType)
      case gl: Generic.TList => collectCustomTypes(gl.valueType)
      case gs: Generic.TSet => collectCustomTypes(gs.valueType)
      case go: Generic.TOption => collectCustomTypes(go.valueType)
    }
    case _ => Seq(id)
  }

  private def withImport(t: TypeId, fromPackage: Package, index: Int): String = {
    if (t.path.domain == DomainId.Builtin) {
      return s""
    }

    if (t.path.toPackage.isEmpty) {
      return s""
    }

    val nestedDepth = t.path.toPackage.zip(fromPackage).count(x => x._1 == x._2)

    if (nestedDepth == t.path.toPackage.size) {
        // It seems that we don't need if namespace is the same, TypeScript should handle resolution itself
        return ""
//      s"import { ${t.path.toPackage.head} as ${t.path.toPackage.head + index} } from ${"\"" + "./" + t.name + "\""};"
    }

    var importOffset = ""
    (1 to (t.path.toPackage.size - nestedDepth)).foreach(_ => importOffset += "../")

    val importFile = importOffset + t.path.toPackage.drop(nestedDepth).mkString("/")+ "/" + t.name

    val pkgName = t.path.toPackage.head + index + "." + t.path.toPackage.drop(1).mkString(".")

    t match {
      case iid: InterfaceId =>
        s"""import { ${t.path.toPackage.head} as ${t.path.toPackage.head + index} } from '$importFile';
           |import { ${t.name}, ${typespace.implId(iid).name}Serialized } from '$importFile';
           |const ${typespace.implId(iid).name} = $pkgName.${typespace.implId(iid).name};
             """.stripMargin

      case _: AdtId =>
        s"import { ${t.name} } from '$importFile';"

      case _: AliasId =>
        s"import { ${t.name} } from '$importFile';"

      case _: IdentifierId =>
        s"""import { ${t.path.toPackage.head} as ${t.path.toPackage.head + index} } from '$importFile';
           |import { I${t.name} } from '$importFile';
           |const ${t.name} = $pkgName.${t.name};
             """.stripMargin

      case _: EnumId =>
        s"import { ${t.name} } from '$importFile';"

      case _: DTOId =>
        s"""import { ${t.path.toPackage.head} as ${t.path.toPackage.head + index} } from '$importFile';
           |import { I${t.name}, I${t.name}Serialized } from '$importFile';
           |const ${t.name} = $pkgName.${t.name};
             """.stripMargin
      case _: Builtin =>
        throw new IDLException(s"Impossible case: user type expected: $t")
    }
  }

  protected def typesToImports(types: Seq[TypeId], pkg: Package): String = {
    val imports = types.distinct
    if (pkg.isEmpty) {
      return ""
    }

    imports.zipWithIndex.map{ case (it, index) => this.withImport(it, pkg, index)}.mkString("\n") + "\n\n"
  }

  protected def renderSerializedObject(fields: List[Field]): String = {
    val serialized = fields.map(f => conv.serializeField(f, typespace))
    val it = serialized.iterator
    val serializedFields = it.map { m => s"$m${if (it.hasNext) "," else ""}" }.mkString("\n")
    serializedFields
  }

  protected def renderInterface(i: Interface): RenderableCogenProduct = {
    val extendsInterfaces =
      if (i.struct.superclasses.interfaces.length > 0) {
        "extends " + i.struct.superclasses.interfaces.map(iface => iface.name).mkString(", ") + " "
      } else {
        ""
      }

    val extendsInterfacesSerialized =
      if (i.struct.superclasses.interfaces.length > 0) {
        "extends " + i.struct.superclasses.interfaces.map(iface => typespace.implId(iface).name + "Serialized").mkString(", ") + " "
      } else {
        ""
      }

    val fields = typespace.structure.structure(i)
    val distinctFields = fields.all.groupBy(_.field.name).map(_._2.head.field)
    val eid = typespace.implId(i.id)

    val iface =
      s"""export interface ${i.id.name} $extendsInterfaces{
         |    getPackageName(): string;
         |    getClassName(): string;
         |    getFullClassName(): string;
         |    serialize(): ${eid.name}Serialized;
         |
         |${fields.all.map(f => s"${conv.toNativeTypeName(conv.safeName(f.field.name), f.field.typeId)}: ${conv.toNativeType(f.field.typeId)};").mkString("\n").shift(4)}
         |}
         |
         |export interface ${eid.name}Serialized $extendsInterfacesSerialized{
         |${fields.all.map(f => s"${conv.toNativeTypeName(f.field.name, f.field.typeId)}: ${conv.toNativeType(f.field.typeId, forSerialized = true, typespace)};").mkString("\n").shift(4)}
         |}
       """.stripMargin

    val importHeader = typesToImports(
        fields.all.flatMap(i => collectCustomTypes(i.field.typeId)).filter(p => p.name != i.id.name && p.path.toPackage.mkString(".") != i.id.path.toPackage.mkString(".")) ++
        i.struct.superclasses.interfaces, i.id.path.toPackage)

    val companion =
      s"""export class ${eid.name} implements ${i.id.name} {
         |${renderRuntimeNames(eid).shift(4)}
         |${fields.all.map(f => conv.toFieldMember(f.field)).mkString("\n").shift(4)}
         |
         |${fields.all.map(f => conv.toFieldMethods(f.field)).mkString("\n").shift(4)}
         |    constructor(data: ${eid.name}Serialized = undefined) {
         |        if (typeof data === 'undefined' || data === null) {
         |            return;
         |        }
         |
         |${distinctFields.map(f => s"${conv.deserializeName("this." + f.name, f.typeId)} = ${conv.deserializeType("data." + f.name, f.typeId, typespace)};").mkString("\n").shift(8)}
         |    }
         |
         |    public serialize(): ${eid.name}Serialized {
         |        return {
         |${renderSerializedObject(distinctFields.toList).shift(12)}
         |        };
         |    }
         |
         |    // Polymorphic section below. If a new type to be registered, use ${eid.name}.register method
         |    // which will add it to the known list. You can also overwrite the existing registrations
         |    // in order to provide extended functionality on existing models, preserving the original class name.
         |
         |    private static _knownPolymorphic: {[key: string]: {new (data?: ${eid.name} | ${eid.name}Serialized): ${i.id.name}}} = {
         |        [${eid.name}.FullClassName]: ${eid.name}
         |    };
         |
         |    public static register(className: string, ctor: {new (data?: ${eid.name} | ${eid.name}Serialized): ${i.id.name}}): void {
         |        this._knownPolymorphic[className] = ctor;
         |    }
         |
         |    public static create(data: {[key: string]: ${eid.name}Serialized}): ${i.id.name} {
         |        const polymorphicId = Object.keys(data)[0];
         |        const ctor = ${eid.name}._knownPolymorphic[polymorphicId];
         |        if (!ctor) {
         |          throw new Error('Unknown polymorphic type ' + polymorphicId + ' for ${eid.name}.Create');
         |        }
         |
         |        return new ctor(data[polymorphicId]);
         |    }
         |}
         |
         |${i.struct.superclasses.interfaces.map(sc => typespace.implId(sc).name + s".register(${eid.name}.FullClassName, ${eid.name});").mkString("\n")}
       """.stripMargin

    ext.extend(i, InterfaceProduct(iface, companion, importHeader, s"// ${i.id.name} Interface"), _.handleInterface)
  }

  protected def renderServiceMethodSignature(method: Service.DefMethod, spread: Boolean = false): String = method match {
    case m: DefMethod.RPCMethod =>
      if (spread) {
        val fields = m.signature.input.fields.map(f => f.name + s": ${conv.toNativeType(f.typeId)}").mkString(", ")
        s"""${m.name}($fields): Promise<${renderServiceMethodOutputSignature(m)}>"""
      } else {
        s"""${m.name}(input: In${m.name.capitalize}): Promise<${renderServiceMethodOutputSignature(m)}>"""
      }
  }

  protected def renderServiceMethodOutputSignature(method: DefMethod.RPCMethod): String = method.signature.output match {
    case _: Struct => s"Out${method.name.capitalize}"
    case al: Algebraic => al.alternatives.map(alt => alt.typeId.name).mkString(" | ")
    case si: Singular => conv.toNativeType(si.typeId, forSerialized = false, typespace)
  }

  protected def renderServiceClientMethod(service: String, method: Service.DefMethod): String = method match {
    case m: DefMethod.RPCMethod => m.signature.output match {
      case _: Struct =>
        s"""public ${renderServiceMethodSignature(method, spread = true)} {
           |    const data = new In${m.name.capitalize}();
           |${m.signature.input.fields.map(f => s"data.${f.name} = ${f.name};").mkString("\n").shift(4)}
           |    return this.send('${m.name}', data, In${m.name.capitalize}, ${renderServiceMethodOutputSignature(m)});
           |}
       """.stripMargin

      case al: Algebraic =>
        s"""public ${renderServiceMethodSignature(method, spread = true)} {
           |    const data = new In${m.name.capitalize}();
           |${m.signature.input.fields.map(f => s"data.${f.name} = ${f.name};").mkString("\n").shift(4)}
           |    return new Promise((resolve, reject) => {
           |        this._transport.send(${service}Client.ClassName, '${m.name}', data)
           |            .then(data => {
           |                try {
           |                    const id = Object.keys(data)[0];
           |                    const content = data[id];
           |                    switch (id) {
           |${al.alternatives.map(a => "case '" + (if (a.memberName.isEmpty) a.typeId.name else a.memberName.get) + "': resolve(" + conv.deserializeType("content", a.typeId, typespace, asAny = true) + "); break;").mkString("\n").shift(24)}
           |                        default:
           |                            throw new Error('Unknown type id ' + id + ' for ${m.name} output.');
           |                    }
           |                } catch(err) {
           |                    this._transport.log(err);
           |                    reject(err);
           |                }
           |             })
           |            .catch(err => {
           |                this._transport.log(err);
           |                reject(err);
           |            });
           |    });
           |}
         """.stripMargin

      case si: Singular =>
        s"""public ${renderServiceMethodSignature(method, spread = true)} {
           |    const data = new In${m.name.capitalize}();
           |${m.signature.input.fields.map(f => s"data.${f.name} = ${f.name};").mkString("\n").shift(4)}
           |    return new Promise((resolve, reject) => {
           |        this._transport.send(${service}Client.ClassName, '${m.name}', data)
           |            .then(data => {
           |                try {
           |                    const output = ${conv.deserializeType("data", si.typeId, typespace)};
           |                    resolve(output);
           |                }
           |                catch(err) {
           |                    this._transport.log(err);
           |                    reject(err);
           |                }
           |            })
           |            .catch(err => {
           |                this._transport.log(err);
           |                reject(err);
           |            });
           |        });
           |}
         """.stripMargin
    }
  }

  protected def renderServiceClient(i: Service): String = {
    s"""export interface I${i.id.name}Client {
       |${i.methods.map(me => renderServiceMethodSignature(me, spread = true)).mkString("\n").shift(4)}
       |}
       |
       |export class ${i.id.name}Client implements I${i.id.name}Client {
       |${renderRuntimeNames(i.id.domain.toPackage.mkString("."), i.id.name, s"${i.id.name}Client").shift(4)}
       |    protected _transport: IClientTransport;
       |
       |    constructor(transport: IClientTransport) {
       |        this._transport = transport;
       |    }
       |
       |    private send<I extends IServiceClientInData, O extends IServiceClientOutData>(method: string, data: I, inputType: {new(): I}, outputType: {new(data: any): O} ): Promise<O> {
       |        return new Promise((resolve, reject) => {
       |            this._transport.send(${i.id.name}Client.ClassName, method, data)
       |                .then(data => {
       |                    try {
       |                        const output = new outputType(data);
       |                        resolve(output);
       |                    }
       |                    catch (err) {
       |                        this._transport.log(err);
       |                        reject(err);
       |                    }
       |                })
       |                .catch( err => {
       |                    this._transport.log(err);
       |                    reject(err);
       |                });
       |            });
       |    }
       |${i.methods.map(me => renderServiceClientMethod(i.id.name, me)).mkString("\n").shift(4)}
       |}
     """.stripMargin
  }

  protected def renderServiceMethodOutModel(name: String, implements: String, out: Service.DefMethod.Output): String = out match {
    case st: Struct => renderServiceMethodInModel(name, implements, st.struct)
    case _ => s""
  }

  protected def renderServiceMethodInModel(name: String, implements: String, structure: SimpleStructure): String = {
    s"""class $name implements $implements {
       |${structure.fields.map(f => conv.toFieldMember(f)).mkString("\n").shift(4)}
       |${structure.fields.map(f => conv.toFieldMethods(f)).mkString("\n").shift(4)}
       |    constructor(data: ${name}Serialized = undefined) {
       |        if (typeof data === 'undefined' || data === null) {
       |            return;
       |        }
       |
       |${structure.fields.map(f => s"${conv.deserializeName("this." + f.name, f.typeId)} = ${conv.deserializeType("data." + f.name, f.typeId, typespace)};").mkString("\n").shift(8)}
       |    }
       |
       |    public serialize(): ${name}Serialized {
       |        return {
       |${renderSerializedObject(structure.fields).shift(12)}
       |        };
       |    }
       |}
       |
       |interface ${name}Serialized {
       |${structure.fields.map(f => s"${conv.toNativeTypeName(f.name, f.typeId)}: ${conv.toNativeType(f.typeId, forSerialized = true, typespace)};").mkString("\n").shift(4)}
       |}
     """.stripMargin
  }

  protected def renderServiceMethodModels(method: Service.DefMethod): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""${renderServiceMethodInModel(s"In${m.name.capitalize}", "IServiceClientInData", m.signature.input)}
         |${renderServiceMethodOutModel(s"Out${m.name.capitalize}", "IServiceClientOutData", m.signature.output)}
       """.stripMargin

  }

  protected def renderServiceModels(i: Service): String = {
    i.methods.map(me => renderServiceMethodModels(me)).mkString("\n")
  }

  protected def renderServiceTransportInterface(i: Service): String = {
    s"""interface IServiceClientInData {
       |    serialize(): any;
       |}
       |
       |interface IServiceClientOutData {
       |}
       |
       |export interface IClientTransport {
       |    send(service: string, method: string, data: IServiceClientInData): Promise<any>
       |    subscribe(packageClass: string, callback: (data: any) => void): void
       |    unsubscribe(packageClass: string, callback: (data: any) => void): void
       |    log(content: string | Error): void
       |}
     """.stripMargin
  }

  protected def renderService(i: Service): RenderableCogenProduct = {
      val typeName = i.id.name

      val svc =
        s"""${renderServiceTransportInterface(i)}
           |${renderServiceModels(i)}
           |${renderServiceClient(i)}
         """.stripMargin


//
//    val importHeader = typesToImports(
//      i.methods.map(im => im.asInstanceOf[DeprecatedRPCMethod]).map(f => f.signature.input.flatMap(f2 => f2)) struct.fields.flatMap(i => collectCustomTypes(i.typeId)) ++
//        i.struct.superclasses.interfaces, i.id.path.toPackage)

    val importHeader = ""

    ServiceProduct(svc, importHeader, s"// $typeName client")
  }
}
