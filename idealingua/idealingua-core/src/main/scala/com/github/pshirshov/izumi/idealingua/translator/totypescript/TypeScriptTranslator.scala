package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.Output.{Algebraic, Singular, Struct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
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
    ).flatten ++ List(buildIndexModule())

    modules
  }

  def buildIndexModule(): Module = {
    val content =
        s"""// Auto-generated, any modifications may be overwritten in the future.
           |// Exporting module for domain ${ts.domain.id.toPackage.mkString(".")}
           |${ts.domain.types.filterNot(_.id.isInstanceOf[AliasId]).map(t => s"export * from './${t.id.name}';").mkString("\n")}
           |${ts.domain.services.map(s => s"export * from './${s.id.name}';").mkString("\n")}
         """.stripMargin

    Module(ModuleId(ts.domain.id.toPackage, "index.ts"), content)
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
       |public static readonly PackageName = '$pkg';
       |public static readonly ClassName = '$name';
       |public static readonly FullClassName = '${pkg + "." + name}';
       |
       |public getPackageName(): string { return ${if(holderName == null) name else holderName}.PackageName; }
       |public getClassName(): string { return ${if(holderName == null) name else holderName}.ClassName; }
       |public getFullClassName(): string { return ${if(holderName == null) name else holderName}.FullClassName; }
       """.stripMargin
  }

  protected def renderDtoInterfaceSerializer(iid: InterfaceId): String = {
    val fields = typespace.structure.structure(iid)
    s"""public to${iid.name}Serialized(): ${iid.name}${typespace.implId(iid).name}Serialized {
       |    return {
       |${renderSerializedObject(fields.all.map(_.field)).shift(8)}
       |    };
       |}
       |
       |public to${iid.name}(): ${iid.name}${typespace.implId(iid).name} {
       |    return new ${iid.name}${typespace.implId(iid).name}(this.to${iid.name}Serialized());
       |}
     """.stripMargin
  }

  protected def renderDefaultValue(id: TypeId): Option[String] = id match {
    case g: Generic => g match {
      case _: Generic.TOption => None
      case _: Generic.TMap => Some("{}")
      case _: Generic.TList => Some("[]")
      case _: Generic.TSet => Some("[]")
    }
    case _ => None
  }

  protected def renderDefaultAssign(to: String, id: TypeId): String = {
    val defVal = renderDefaultValue(id)
    if (defVal.isDefined)
      s"$to = ${defVal.get};"
    else
      s""
  }

  protected def renderDto(i: DTO): RenderableCogenProduct = {
    val imports = TypeScriptImports(ts, i, i.id.path.toPackage)
    val fields = typespace.structure.structure(i).all
    val distinctFields = fields.groupBy(_.field.name).map(_._2.head.field)

    val implementsInterfaces =
      if (i.struct.superclasses.interfaces.nonEmpty) {
        "implements " + i.struct.superclasses.interfaces.map(iface => iface.name).mkString(", ") + " "
      } else {
        ""
      }

    val extendsInterfacesSerialized =
      if (i.struct.superclasses.interfaces.nonEmpty) {
        "extends " + i.struct.superclasses.interfaces.map(iface => s"${iface.name}${typespace.implId(iface).name}Serialized").mkString(", ") + " "
      } else {
        ""
      }

    val uniqueInterfaces = ts.inheritance.parentsInherited(i.id).groupBy(_.name).map(_._2.head)
    val dto =
      s"""export class ${i.id.name} $implementsInterfaces {
         |${renderRuntimeNames(i.id).shift(4)}
         |${distinctFields.map(f => conv.toFieldMember(f, ts)).mkString("\n").shift(4)}
         |
         |${distinctFields.map(f => conv.toFieldMethods(f, ts)).mkString("\n").shift(4)}
         |    constructor(data: ${i.id.name}Serialized = undefined) {
         |        if (typeof data === 'undefined' || data === null) {
         |${distinctFields.map(f => renderDefaultAssign(conv.deserializeName("this." + conv.safeName(f.name), f.typeId), f.typeId)).filterNot(_.isEmpty).mkString("\n").shift(12)}
         |            return;
         |        }
         |
         |${distinctFields.map(f => s"${conv.deserializeName("this." + conv.safeName(f.name), f.typeId)} = ${conv.deserializeType("data." + f.name, f.typeId, typespace)};").mkString("\n").shift(8)}
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
         |${distinctFields.map(f => s"${conv.toNativeTypeName(f.name, f.typeId)}: ${conv.toNativeType(f.typeId, ts, forSerialized = true)};").mkString("\n").shift(4)}
         |}
         |
         |${uniqueInterfaces.map(sc => sc.name + typespace.implId(sc).name + s".register(${i.id.name}.FullClassName, ${i.id.name});").mkString("\n")}
         """.stripMargin

    CompositeProduct(dto, imports.render(ts), s"// ${i.id.name} DTO")
  }

  protected def renderAlias(i: Alias): RenderableCogenProduct = {
//      val imports = TypeScriptImports(i, i.id.path.toPackage)
//      AliasProduct(
//        s"""export type ${i.id.name} = ${conv.toNativeType(i.target)};
//           |$aliasConstuctor
//        """.stripMargin,
//        imports.render(ts),
//        s"// ${i.id.name} alias"
//      )

      AliasProduct(
        s"""// TypeScript does not natively support well type aliases.
           |// Normally the code would be:
           |// export type ${i.id.name} = ${conv.toNativeType(i.target, ts)};
           |//
           |// However, constructors and casting won't work correctly.
           |// Therefore, all aliases usage was just replaced with the target
           |// type and this file is for reference purposes only.
           |// Should the new versions of TypeScript support this better -
           |// it can be enabled back.
           |//
           |// See this and other referenced threads for more information:
           |// https://github.com/Microsoft/TypeScript/issues/2552
          """.stripMargin
      )
  }

  protected def renderAdt(i: Adt): RenderableCogenProduct = {
    val imports = TypeScriptImports(ts, i, i.id.path.toPackage)
    val base =
      s"""export type ${i.id.name} = ${i.alternatives.map(alt => alt.typeId.name).mkString(" | ")};
         |
         |export class ${i.id.name}Helpers {
         |    public static serialize(adt: ${i.id.name}): {[key: string]: ${i.alternatives.map(alt => (if (alt.typeId.isInstanceOf[InterfaceId]) alt.name + typespace.implId(alt.typeId.asInstanceOf[InterfaceId]).name else alt.typeId.name) + "Serialized").mkString(" | ")}} {
         |        let className = adt.getClassName();
         |${i.alternatives.filter(al => al.memberName.isDefined).map(a => s"if (className == '${a.typeId.name}') {\n    className = '${a.memberName.get}'\n}").mkString("\n").shift(8)}
         |        return {
         |            [className]: adt.serialize()
         |        };
         |    }
         |
         |    public static deserialize(data: {[key: string]: ${i.alternatives.map(alt => (if (alt.typeId.isInstanceOf[InterfaceId]) alt.name + typespace.implId(alt.typeId.asInstanceOf[InterfaceId]).name else alt.typeId.name) + "Serialized").mkString(" | ")}}): ${i.id.name} {
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

    AdtProduct(
      base,
      imports.render(ts),
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
      val imports = TypeScriptImports(ts, i, i.id.path.toPackage)
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
           |${fields.all.map(f => s"${conv.toNativeTypeName(conv.safeName(f.field.name), f.field.typeId)}: ${conv.toNativeType(f.field.typeId, ts, forSerialized = true)};").mkString("\n").shift(4)}
           |}
         """.stripMargin

      val identifier =
        s"""export class $typeName implements I$typeName {
           |${renderRuntimeNames(i.id).shift(4)}
           |${fields.all.map(f => conv.toFieldMember(f.field, ts)).mkString("\n").shift(4)}
           |
           |${fields.all.map(f => conv.toFieldMethods(f.field, ts)).mkString("\n").shift(4)}
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
           |${sortedFields.zipWithIndex.map{ case (sf, index) => s"this.${conv.safeName(sf.field.name)} = ${conv.parseTypeFromString(s"decodeURIComponent(parts[$index])", sf.field.typeId)};"}.mkString("\n").shift(12)}
           |        } else {
           |${fields.all.map(f => s"this.${conv.safeName(f.field.name)} = ${conv.deserializeType("data." + f.field.name, f.field.typeId, typespace)};").mkString("\n").shift(12)}
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

    ext.extend(i, IdentifierProduct(identifier, identifierInterface, imports.render(ts), s"// ${i.id.name} Identifier"), _.handleIdentifier)
  }

  protected def renderSerializedObject(fields: List[Field]): String = {
    val serialized = fields.map(f => conv.serializeField(f, typespace))
    val it = serialized.iterator
    it.map { m => s"$m${if (it.hasNext) "," else ""}" }.mkString("\n")
  }

  protected def renderInterface(i: Interface): RenderableCogenProduct = {
    val imports = TypeScriptImports(ts, i, i.id.path.toPackage)
    val extendsInterfaces =
      if (i.struct.superclasses.interfaces.nonEmpty) {
        "extends " + i.struct.superclasses.interfaces.map(iface => iface.name).mkString(", ") + " "
      } else {
        ""
      }

    val extendsInterfacesSerialized =
      if (i.struct.superclasses.interfaces.nonEmpty) {
        "extends " + i.struct.superclasses.interfaces.map(iface => iface.name + typespace.implId(iface).name + "Serialized").mkString(", ") + " "
      } else {
        ""
      }

    val fields = typespace.structure.structure(i)
    val distinctFields = fields.all.groupBy(_.field.name).map(_._2.head.field)
    val implId = typespace.implId(i.id)
    val eid = i.id.name + implId.name


    val iface =
      s"""export interface ${i.id.name} $extendsInterfaces{
         |    getPackageName(): string;
         |    getClassName(): string;
         |    getFullClassName(): string;
         |    serialize(): ${eid}Serialized;
         |
         |${fields.all.map(f => s"${conv.toNativeTypeName(conv.safeName(f.field.name), f.field.typeId)}: ${conv.toNativeType(f.field.typeId, ts)};").mkString("\n").shift(4)}
         |}
         |
         |export interface ${eid}Serialized $extendsInterfacesSerialized{
         |${fields.all.map(f => s"${conv.toNativeTypeName(f.field.name, f.field.typeId)}: ${conv.toNativeType(f.field.typeId, ts, forSerialized = true)};").mkString("\n").shift(4)}
         |}
       """.stripMargin

    val uniqueInterfaces = ts.inheritance.parentsInherited(i.id).groupBy(_.name).map(_._2.head)
    val companion =
      s"""export class ${eid} implements ${i.id.name} {
         |${renderRuntimeNames(implId.path.toPackage.mkString("."), implId.name, eid).shift(4)}
         |${fields.all.map(f => conv.toFieldMember(f.field, ts)).mkString("\n").shift(4)}
         |
         |${fields.all.map(f => conv.toFieldMethods(f.field, ts)).mkString("\n").shift(4)}
         |    constructor(data: ${eid}Serialized = undefined) {
         |        if (typeof data === 'undefined' || data === null) {
         |${distinctFields.map(f => renderDefaultAssign(conv.deserializeName("this." + conv.safeName(f.name), f.typeId), f.typeId)).filterNot(_.isEmpty).mkString("\n").shift(12)}
         |            return;
         |        }
         |
         |${distinctFields.map(f => s"${conv.deserializeName("this." + conv.safeName(f.name), f.typeId)} = ${conv.deserializeType("data." + f.name, f.typeId, typespace)};").mkString("\n").shift(8)}
         |    }
         |
         |    public serialize(): ${eid}Serialized {
         |        return {
         |${renderSerializedObject(distinctFields.toList).shift(12)}
         |        };
         |    }
         |
         |    // Polymorphic section below. If a new type to be registered, use ${eid}.register method
         |    // which will add it to the known list. You can also overwrite the existing registrations
         |    // in order to provide extended functionality on existing models, preserving the original class name.
         |
         |    private static _knownPolymorphic: {[key: string]: {new (data?: ${eid} | ${eid}Serialized): ${i.id.name}}} = {
         |        [${eid}.FullClassName]: ${eid}
         |    };
         |
         |    public static register(className: string, ctor: {new (data?: ${eid} | ${eid}Serialized): ${i.id.name}}): void {
         |        this._knownPolymorphic[className] = ctor;
         |    }
         |
         |    public static create(data: {[key: string]: ${eid}Serialized}): ${i.id.name} {
         |        const polymorphicId = Object.keys(data)[0];
         |        const ctor = ${eid}._knownPolymorphic[polymorphicId];
         |        if (!ctor) {
         |          throw new Error('Unknown polymorphic type ' + polymorphicId + ' for ${eid}.Create');
         |        }
         |
         |        return new ctor(data[polymorphicId]);
         |    }
         |}
         |
         |${uniqueInterfaces.map(sc => sc.name + typespace.implId(sc).name + s".register(${eid}.FullClassName, ${eid});").mkString("\n")}
       """.stripMargin

    ext.extend(i, InterfaceProduct(iface, companion, imports.render(ts), s"// ${i.id.name} Interface"), _.handleInterface)
  }

  protected def renderServiceMethodSignature(method: Service.DefMethod, spread: Boolean = false): String = method match {
    case m: DefMethod.RPCMethod =>
      if (spread) {
        val fields = m.signature.input.fields.map(f => conv.safeName(f.name) + s": ${conv.toNativeType(f.typeId, ts)}").mkString(", ")
        s"""${m.name}($fields): Promise<${renderServiceMethodOutputSignature(m)}>"""
      } else {
        s"""${m.name}(input: In${m.name.capitalize}): Promise<${renderServiceMethodOutputSignature(m)}>"""
      }
  }

  protected def renderServiceMethodOutputSignature(method: DefMethod.RPCMethod): String = method.signature.output match {
    case _: Struct => s"Out${method.name.capitalize}"
    case al: Algebraic => al.alternatives.map(alt => conv.toNativeType(alt.typeId, ts)).mkString(" | ")
    case si: Singular => conv.toNativeType(si.typeId, ts)
  }

  protected def renderServiceClientMethod(service: String, method: Service.DefMethod): String = method match {
    case m: DefMethod.RPCMethod => m.signature.output match {
      case _: Struct =>
        s"""public ${renderServiceMethodSignature(method, spread = true)} {
           |    const __data = new In${m.name.capitalize}();
           |${m.signature.input.fields.map(f => s"__data.${conv.safeName(f.name)} = ${conv.safeName(f.name)};").mkString("\n").shift(4)}
           |    return this.send('${m.name}', __data, In${m.name.capitalize}, ${renderServiceMethodOutputSignature(m)});
           |}
       """.stripMargin

      case al: Algebraic =>
        s"""public ${renderServiceMethodSignature(method, spread = true)} {
           |    const __data = new In${m.name.capitalize}();
           |${m.signature.input.fields.map(f => s"__data.${conv.safeName(f.name)} = ${conv.safeName(f.name)};").mkString("\n").shift(4)}
           |    return new Promise((resolve, reject) => {
           |        this._transport.send(${service}Client.ClassName, '${m.name}', __data)
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
           |    const __data = new In${m.name.capitalize}();
           |${m.signature.input.fields.map(f => s"__data.${conv.safeName(f.name)} = ${conv.safeName(f.name)};").mkString("\n").shift(4)}
           |    return new Promise((resolve, reject) => {
           |        this._transport.send(${service}Client.ClassName, '${m.name}', __data)
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
       |    protected _transport: IRTClientTransport;
       |
       |    constructor(transport: IRTClientTransport) {
       |        this._transport = transport;
       |    }
       |
       |    private send<I extends IRTServiceClientInData, O extends IRTServiceClientOutData>(method: string, data: I, inputType: {new(): I}, outputType: {new(data: any): O} ): Promise<O> {
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
//    case al: Algebraic => renderAdt(al)
    case _ => s""
  }

  protected def renderServiceMethodInModel(name: String, implements: String, structure: SimpleStructure): String = {
    s"""class $name implements $implements {
       |${structure.fields.map(f => conv.toFieldMember(f, ts)).mkString("\n").shift(4)}
       |${structure.fields.map(f => conv.toFieldMethods(f, ts)).mkString("\n").shift(4)}
       |    constructor(data: ${name}Serialized = undefined) {
       |        if (typeof data === 'undefined' || data === null) {
       |            return;
       |        }
       |
       |${structure.fields.map(f => s"${conv.deserializeName("this." + conv.safeName(f.name), f.typeId)} = ${conv.deserializeType("data." + f.name, f.typeId, typespace)};").mkString("\n").shift(8)}
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
       |${structure.fields.map(f => s"${conv.toNativeTypeName(f.name, f.typeId)}: ${conv.toNativeType(f.typeId, ts, forSerialized = true)};").mkString("\n").shift(4)}
       |}
     """.stripMargin
  }

  protected def renderServiceMethodModels(method: Service.DefMethod): String = method match {
    case m: DefMethod.RPCMethod =>
      s"""${renderServiceMethodInModel(s"In${m.name.capitalize}", "IRTServiceClientInData", m.signature.input)}
         |${renderServiceMethodOutModel(s"Out${m.name.capitalize}", "IRTServiceClientOutData", m.signature.output)}
       """.stripMargin

  }

  protected def renderServiceModels(i: Service): String = {
    i.methods.map(me => renderServiceMethodModels(me)).mkString("\n")
  }

  protected def importFromIRT(names: List[String], pkg: Package): String = {
    var importOffset = ""
    (1 to pkg.length).foreach(_ => importOffset += "../")
    s"""import {
       |${names.map(n => s"    $n").mkString(",\n")}
       |} from '${importOffset}irt'
     """.stripMargin
  }

  protected def renderService(i: Service): RenderableCogenProduct = {
      val imports = TypeScriptImports(ts, i, i.id.domain.toPackage, List.empty)
      val typeName = i.id.name

      val svc =
        s"""${renderServiceModels(i)}
           |${renderServiceClient(i)}
         """.stripMargin

      val header =
        s"""${imports.render(ts)}
           |${importFromIRT(List("IRTServiceClientInData", "IRTServiceClientOutData", "IRTClientTransport"), i.id.domain.toPackage)}
         """.stripMargin

    ServiceProduct(svc, header, s"// $typeName client")
  }
}
