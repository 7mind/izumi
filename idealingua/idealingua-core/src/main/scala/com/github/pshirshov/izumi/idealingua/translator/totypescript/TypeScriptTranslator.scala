package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Field, Service, TypeDef}
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TSTContext
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.{EnumHelpersExtension, TypeScriptTranslatorExtension}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.products.CogenProduct._
import com.github.pshirshov.izumi.idealingua.translator.totypescript.products.{CogenProduct, RenderableCogenProduct}
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

import scala.meta._

object TypeScriptTranslator {
  final val defaultExtensions = Seq(
    EnumHelpersExtension
  )
}

class TypeScriptTranslator(ts: Typespace, extensions: Seq[TypeScriptTranslatorExtension]) {
  protected val ctx: TSTContext = new TSTContext(ts, extensions)

  import ctx._
  import conv._

  def translate(): Seq[Module] = {
    val modules = Seq(
      typespace.domain.types.flatMap(translateDef)
      , typespace.domain.services.flatMap(translateService)
    ).flatten

    modules
  }


  protected def translateService(definition: Service): Seq[Module] = {
    ctx.modules.toSource(IndefiniteId(definition.id), ctx.modules.toModuleId(definition.id), renderService(definition))
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

    ctx.modules.toSource(IndefiniteId(definition.id), ctx.modules.toModuleId(definition), defns)
  }

  protected def renderRuntimeNames(i: TypeId): String = {
      renderRuntimeNames(i.pkg.mkString("."), i.name)
  }

  protected def renderRuntimeNames(pkg: String, name: String): String = {
    s"""// Runtime identification methods
       |public static readonly PackageName = ${"\"" + pkg + "\""};
       |public static readonly ClassName = ${"\"" + name + "\""};
       |public static readonly FullClassName = ${name}.PackageName + "." + ${name}.ClassName;
       |
       |public getPackageName(): string { return ${name}.PackageName; }
       |public getClassName(): string { return ${name}.ClassName; }
       |public getFullClassName(): string { return ${name}.FullClassName; }
       """.stripMargin
  }

  protected def renderDtoInterfaceSerializer(iid: InterfaceId): String = {
    val fields = typespace.structure.structure(iid)
    s"""public to${iid.name}(): ${iid.name} {
       |    return {
       |${renderSerializedObject(fields.all.map(_.field)).shift(8)}
       |    };
       |}
     """.stripMargin
  }

  protected def renderDto(i: DTO): RenderableCogenProduct = {
    val dtoIfaces = i.struct.superclasses.interfaces.map(si => {
      val fields = typespace.structure.structure(si)
      s"""export interface ${si.name} {
         |${fields.all.map(f => s"${f.field.name}: ${conv.toNativeType(f.field.typeId, true)};").mkString("\n").shift(4)}
         |}
         """.stripMargin
    }).mkString("\n")

    val fields = typespace.structure.structure(i).all
    val distinctFields = fields.groupBy(_.field.name).map(_._2.head.field)

    val importHeader = typesToImports(
      i.struct.fields.flatMap(i => collectCustomTypes(i.typeId)) ++
        i.struct.superclasses.interfaces, i.id.pkg)

    val extendsInterfaces =
      if (i.struct.superclasses.interfaces.length > 0) {
        "extends " + i.struct.superclasses.interfaces.map(iface => iface.name).mkString(", ") + " "
      } else {
        ""
      }

    val dto =
      s"""export class ${i.id.name} implements I${i.id.name} {
         |${renderRuntimeNames(i.id).shift(4)}
         |${distinctFields.map(f => conv.toFieldMember(f)).mkString("\n").shift(4)}
         |
         |${distinctFields.map(f => conv.toFieldMethods(f)).mkString("\n").shift(4)}

         |    constructor(data: I${i.id.name} = undefined) {
         |        if (data) {
         |            // If data is a class instance, we make a clone of it by serializing data and then using it
         |            data = data instanceof ${i.id.name} ? data.serialize() : data;
         |${distinctFields.map(f => s"this.${f.name} = ${conv.deserializeType("data." + f.name, f.typeId)};").mkString("\n").shift(12)}
         |        }
         |    }
         |
         |${i.struct.superclasses.interfaces.map(si => renderDtoInterfaceSerializer(si)).mkString("\n").shift(4)}
         |
         |    public serialize(): I${i.id.name} {
         |        return {
         |${renderSerializedObject(distinctFields.toList).shift(12)}
         |        };
         |    }
         |}
         |
         |export interface I${i.id.name} ${extendsInterfaces}{
         |${distinctFields.map(f => s"${f.name}: ${conv.toNativeType(f.typeId, true)};").mkString("\n").shift(4)}
         |}
         |
         |${dtoIfaces}
         """.stripMargin

    CompositeProduct(dto, importHeader, s"// ${i.id.name} DTO")
  }

  protected def renderAlias(i: Alias): RenderableCogenProduct = {
      AliasProduct(
        s"type ${i.id.name} = ${conv.toNativeType(i.target)};",
        s"",
        s"// ${i.id.name} alias"
      )
  }

  protected def renderAdt(i: Adt): RenderableCogenProduct = {
    val base =
      s"""type ${i.id.name} = ${i.alternatives.map(alt => alt.typeId).mkString(" | ")};
         |
         |export class ${i.id.name}Helpers {
         |    public static serialize(adt: ${i.id.name}): {[key: string]: ${i.alternatives.map(alt => alt.typeId).mkString(" | ")}} {
         |        return {
         |
         |        };
         |    }
         |
         |    public static deserialize(data: {[key: string]: ${i.alternatives.map(alt => alt.typeId).mkString(" | ")}}): ${i.id.name} {
         |        const adtId = Object.keys(data)[0];
         |        const adtContent = data[adtId];
         |        switch (adtId) {
         |${i.alternatives.map(a => "'" + a.typeId.name + "': " + conv.deserializeType("adtContent", a.typeId)).mkString("\n").shift(12)}
         |        }
         |    }
         |}
       """.stripMargin

    val importHeader = typesToImports(i.alternatives.map(ii => ii.typeId), i.id.pkg)

    AdtProduct(
      base,
      importHeader,
      s"// ${i.id.name} Algebraic Data Type"
    )
  }

  protected def renderEnumeration(i: Enumeration): RenderableCogenProduct = {
    val it = i.members.iterator
    val members = it.map { m =>
      s"${m} = '${m}'" + (if (it.hasNext) "," else "")
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
        s"""export interface I${typeName} {
           |${fields.all.map(f => s"${f.field.name}: ${conv.toNativeType(f.field.typeId, true)};").mkString("\n").shift(4)}
           |}
         """.stripMargin

      val identifier =
        s"""export class ${typeName} implements I${typeName} {
           |${renderRuntimeNames(i.id).shift(4)}
           |${fields.all.map(f => conv.toFieldMember(f.field)).mkString("\n").shift(4)}
           |
           |${fields.all.map(f => conv.toFieldMethods(f.field)).mkString("\n").shift(4)}
           |    constructor(data: string | I${typeName} = undefined) {
           |        if (data) {
           |            if (typeof data === 'string') {
           |                const parts = data.split(':');
           |${sortedFields.zipWithIndex.map{ case (sf, index) => s"this.${sf.field.name} = ${conv.parseTypeFromString(s"decodeURIComponent(parts[${index}])", sf.field.typeId)};"}.mkString("\n").shift(16)}
           |            } else {
           |${fields.all.map(f => s"this.${f.field.name} = ${conv.deserializeType("data." + f.field.name, f.field.typeId)};").mkString("\n").shift(16)}
           |            }
           |        }
           |    }
           |
           |    public toString(): string {
           |        const suffix = ${sortedFields.map(sf => "encodeURIComponent(this." + sf.field.name + ")").mkString(" + ':' + ")};
           |        return '${typeName}#' + suffix;
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

  protected def withConstImport(t: TypeId, fromPackage: Package, index: Int): String = {
        val pkgName = t.pkg.head + index + "." + t.pkg.drop(1).mkString(".")

        t match {
          case iid: InterfaceId =>
            s"""const ${t.name} = ${pkgName}.${t.name};
               |const ${typespace.implId(iid).name} = ${pkgName}.${typespace.implId(iid).name}
             """.stripMargin

          case _: AdtId => s"const ${t.name} = ${pkgName}.${t.name};"

          case _: AliasId => s"const ${t.name} = ${pkgName}.${t.name};"

          case _: IdentifierId =>
            s"""const ${t.name} = ${pkgName}.${t.name};
               |const I${t.name} = ${pkgName}.I${t.name};
             """.stripMargin

          case _: EnumId => s"const ${t.name} = ${pkgName}.${t.name};"
          case _: DTOId =>
            s"""const ${t.name} = ${pkgName}.${t.name};
               |const I${t.name} = ${pkgName}.I${t.name};
             """.stripMargin
        }
  }

  private def withImport(t: TypeId, fromPackage: Package, index: Int): String = {
    val nestedDepth = t.pkg.zip(fromPackage).filter(x => x._1 == x._2).size

    if (nestedDepth == t.pkg.size) {
      s"import { ${t.pkg.head + index} } from ${"\"" + "./" + t.name + "\""};"
    } else {
      var importOffset = ""
      (1 to (t.pkg.size - nestedDepth + 1)).foreach(_ => importOffset += "../")
      s"import { ${t.pkg.head + index} } from ${"\"" + importOffset + t.pkg.drop(nestedDepth - 1).mkString("/") + t.name + "\""};"
    }
  }

  protected def typesToImports(types: Seq[TypeId], pkg: Package): String = {
    val imports = types.distinct
    if (pkg.isEmpty) {
      return ""
    }

    imports.zipWithIndex.map{ case (it, index) => this.withImport(it, pkg, index)}.mkString("\n") + "\n\n" +
    imports.zipWithIndex.map{ case (it, index) => this.withConstImport(it, pkg, index)}.mkString("\n") + "\n\n"
  }

  protected def renderSerializedObject(fields: List[Field]): String = {
    val serialized = fields.map(f => conv.serializeField(f))
    val it = serialized.iterator
    val serializedFields = it.map { m => s"${m}${if (it.hasNext) "," else ""}" }.mkString("\n")
    serializedFields
  }

  protected def renderInterface(i: Interface): RenderableCogenProduct = {
    val extendsInterfaces =
      if (i.struct.superclasses.interfaces.length > 0) {
        "extends " + i.struct.superclasses.interfaces.map(iface => iface.name).mkString(", ") + " "
      } else {
        ""
      }

    val iface =
      s"""export interface ${i.id.name} ${extendsInterfaces}{
         |${i.struct.fields.map(f => s"${f.name}: ${conv.toNativeType(f.typeId, true)};").mkString("\n").shift(4)}
         |}
       """.stripMargin

    val importHeader = typesToImports(
        i.struct.fields.flatMap(i => collectCustomTypes(i.typeId)) ++
        i.struct.superclasses.interfaces, i.id.pkg)

    // Render now companion object
    val fields = typespace.structure.structure(i)
    val distinctFields = fields.all.groupBy(_.field.name).map(_._2.head.field)
    val eid = typespace.implId(i.id)

    val companion =
      s"""export class ${eid.name} implements ${i.id.name} {
         |${renderRuntimeNames(eid).shift(4)}
         |${fields.all.map(f => conv.toFieldMember(f.field)).mkString("\n").shift(4)}
         |
         |${fields.all.map(f => conv.toFieldMethods(f.field)).mkString("\n").shift(4)}
         |    constructor(data: ${i.id.name} = undefined) {
         |        if (data) {
         |            // If data is a class instance, we make a clone of it by serializing data and then using it
         |            data = data instanceof ${eid.name} ? data.serialize() : data;
         |${distinctFields.map(f => s"this.${f.name} = ${conv.deserializeType("data." + f.name, f.typeId)};").mkString("\n").shift(12)}
         |        }
         |    }
         |
         |    public serialize(): ${i.id.name} {
         |        return {
         |${renderSerializedObject(distinctFields.toList).shift(12)}
         |        };
         |    }
         |
         |    // Polymorphic section below. If a new type to be registered, use ${eid.name}.register method
         |    // which will add it to the known list. You can also overwrite the existing registrations
         |    // in order to provide extended functionality on existing models, preserving the original class name.
         |
         |    private static _knownPolymorphic: {[key: string]: {new (data?: ${i.id.name}): ${i.id.name}}} = {
         |        [${eid.name}.FullClassName]: ${eid.name}
         |    };
         |
         |    public static register(className: string, ctor: {new (data?: ${i.id.name}): ${i.id.name}}): void {
         |        this._knownPolymorphic[className] = ctor;
         |    }
         |
         |    public static create(data: {[key: string]: ${i.id.name}}): ${i.id.name} {
         |        const polymorphicId = Object.keys(data)[0];
         |        const ctor = ${eid.name}._knownPolymorphic[polymorphicId];
         |        if (!ctor) {
         |          throw new Error('Unknown polymorphic type ' + polymorphicId + ' for ${eid.name}.Create');
         |        }
         |
         |        return ctor(data[polymorphicId]);
         |    }
         |}
       """.stripMargin

    ext.extend(i, InterfaceProduct(iface, companion, importHeader, s"// ${i.id.name} Interface"), _.handleInterface)
  }

  protected def renderServiceMethodSignature(method: DeprecatedRPCMethod, spread: Boolean = false): String = {
    if (spread) {
      val fields = method.signature.input.map(typespace.structure.structure)
      s"""${method.name}(${fields.map(f=> f.all)})"""
    } else {
      s"""${method.name}(input: In${method.name.capitalize}): Promise<Out${method.name.capitalize}>"""
    }
  }

  protected def renderServiceMethod(service: String, method: DeprecatedRPCMethod): String = {
    s"""${renderServiceMethodSignature(method)} {
       |    return this._transport.send(${service}.ClassName, '${method.name}', input)
       |        .then(data => {
       |            return data as Out${method.name.capitalize};
       |        })
       |        .catch( err => {
       |            this._transport.log(err);
       |        });
       |    return undefined;
       |}
     """.stripMargin
  }

  protected def renderService(i: Service): RenderableCogenProduct = {
      val typeName = i.id.name

      val svc =
        s"""export interface I${typeName}Client {
           |${i.methods.map(me => renderServiceMethodSignature(me.asInstanceOf[DeprecatedRPCMethod])).mkString("\n").shift(4)}
           |}
           |
           |interface ${typeName.capitalize}Transport {
           |    send(service: string, method: string, data: any): Promise<any>
           |    subscribe(packageClass: string, callback: (data: any) => void): void
           |    unsubscribe(packageClass: string, callback: (data: any) => void): void
           |    log(content: string | Error): void
           |}
           |
           |export class ${typeName} implements I${typeName} {
           |${renderRuntimeNames(i.id.pkg.mkString("."), i.id.name).shift(4)}
           |    protected _transport: ${typeName.capitalize}Transport;
           |
           |    constructor(transport: ${typeName.capitalize}Transport) {
           |        this._transport = transport;
           |    }
           |
           |${i.methods.map(me => "public " + renderServiceMethod(i.id.name, me.asInstanceOf[DeprecatedRPCMethod])).mkString("\n").shift(4)}
           |}
         """.stripMargin
//
//    val importHeader = typesToImports(
//      i.methods.map(im => im.asInstanceOf[DeprecatedRPCMethod]).map(f => f.signature.input.flatMap(f2 => f2)) struct.fields.flatMap(i => collectCustomTypes(i.typeId)) ++
//        i.struct.superclasses.interfaces, i.id.pkg)

    val importHeader = ""

    ServiceProduct(svc, importHeader, s"// ${typeName} client")
  }
}
