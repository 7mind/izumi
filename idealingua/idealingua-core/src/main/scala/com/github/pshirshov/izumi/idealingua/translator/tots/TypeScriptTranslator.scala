package com.github.pshirshov.izumi.idealingua.translator.tots

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.Generic.{TList, TMap, TOption, TSet}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Indefinite, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il._
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}

import scala.collection.mutable

class TypeScriptTranslator(typespace: Typespace) {

  protected val packageObjects: mutable.HashMap[ModuleId, mutable.ArrayBuffer[String]] = mutable.HashMap[ModuleId, mutable.ArrayBuffer[String]]()

  def translate(): Seq[Module] = {
    typespace.domain
      .types
      .flatMap(translateDef) ++
      packageObjects.map {
        case (id, content) =>
          // TODO: dirty!
          val pkgName = id.name.split('.').head

          val code =
            Seq(
              s"export namespace $pkgName {",
              s""
            ) ++
            content.map("    " + _.toString()) ++
            Seq(
              s"",
              s"}"
            )
          Module(id, withPackage(id.path.init, code, Seq.empty))
      } ++
      typespace.domain.services.flatMap(translateService)
  }

  protected def translateDef(definition: ILAst): Seq[Module] = {
    val defns = definition match {
      case a: Alias =>
        packageObjects.getOrElseUpdate(toModuleId(a), mutable.ArrayBuffer()) ++= renderAlias(a)
        Seq.empty
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
    }

    val importTypes = definition match {
      case a: Alias =>
        collectAliasImports(a)
      case i: Identifier =>
        collectIdentifierImports(i)
      case i: Interface =>
        collectInterfaceImports(i)
      case d: DTO =>
        collectDtoImports(d)
      case _ => Seq.empty
    }

    if (defns.nonEmpty) {
      toSource(Indefinite(definition.id), toModuleId(definition), defns, importTypes)
    } else {
      Seq.empty
    }
  }

  private def toModuleId(id: TypeId): ModuleId = {
    ModuleId(id.pkg, s"${id.name}.ts")
  }

  private def toModuleId(defn: ILAst): ModuleId = {
    defn match {
      case i: Alias =>
        val concrete = i.id
        ModuleId(concrete.pkg, s"${concrete.pkg.last}.ts")

      case other =>
        val id = other.id
        toModuleId(id)
    }
  }

  protected def translateService(definition: Service): Seq[Module] = {
    toSource(Indefinite(definition.id), toModuleId(definition.id), renderService(definition), Seq.empty)
  }

  private def toSource(id: Indefinite, moduleId: ModuleId, traitDef: Seq[String], importTypes: Seq[TypeId]) = {
    val code = traitDef.map(_.toString()) ++ Seq("")
    val content: String = withPackage(id.pkg, code, importTypes)
    Seq(Module(moduleId, content))
  }

  private def withImport(t: TypeId, fromPackage: idealingua.model.common.Package, index: Int) = {
    val pkgName = t.pkg.head + index + "." + t.pkg.drop(1).mkString(".")

    t match {
      case _: InterfaceId => Seq(s"const I${t.name} = ${pkgName}.I${t.name};")
      case _: AliasId => Seq(s"const ${t.name} = ${pkgName}.${t.name};")
      case _: IdentifierId => Seq(
        s"const ${t.name} = ${pkgName}.${t.name};",
        s"const I${t.name} = ${pkgName}.I${t.name};"
      )
      case _: EnumId => Seq(s"const ${t.name} = ${pkgName}.${t.name};")
      case _: DTOId => Seq(
        s"const ${t.name} = ${pkgName}.${t.name};",
        s"const I${t.name} = ${pkgName}.I${t.name};"
      )
    }
  }

  private def withPackage(pkg: idealingua.model.common.Package, code: Seq[String], importTypes: Seq[TypeId]) = {
    val content = if (pkg.isEmpty) {
      code.mkString("\n")
    } else {
      val distinctImport = importTypes.distinct
      (distinctImport.zipWithIndex.map{ case (it, index) => s"import { ${it.pkg.head + index} } from ${"\"" + "./" + it.name + "\""};" } ++
        distinctImport.zipWithIndex.flatMap{ case (it, index) => this.withImport(it, pkg, index)} ++
      Seq(
        s"",
        s"export namespace ${pkg.mkString(".")} {"
      ) ++
      code.map(s => "    " + s) ++
      Seq(
        s"}"
      )).mkString("\n")
    }
    content
  }

  def renderEnumeration(i: Enumeration): Seq[String] = {
    val duplicates = i.members.groupBy(v => v).filter(_._2.lengthCompare(1) > 0)
    if (duplicates.nonEmpty) {
      throw new IDLException(s"Duplicated enum elements: $duplicates")
    }

    val it = i.members.iterator
    val members = it.map { m => s"    ${m} = '${m}'" + (if (it.hasNext) "," else "") }

    Seq(
      s"export enum ${i.id.name} {"
    ) ++
    members ++
    Seq(
      s"}"
    )
  }

  protected def typeToNative(t: TypeId, forInterface: Boolean = false): String = {
    t match {
      case Primitive.TInt8 => "number"
      case Primitive.TInt16 => "number"
      case Primitive.TInt32 => "number"
      case Primitive.TInt64 => "number"
      case Primitive.TBool => "bool"
      case Primitive.TDate => "Date"
      case Primitive.TDouble => "number"
      case Primitive.TFloat => "number"
      case Primitive.TString => "string"
      case Primitive.TTime => "Date"
      case Primitive.TTs => "number"
      case Primitive.TTsTz => "number"
      case Primitive.TUUID => "string"
      case _: TList => this.typeToNative(t.asInstanceOf[TList].valueType, forInterface) + "[]"
      // TODO We must add something kind of substitute for a set here
      case _: TSet => this.typeToNative(t.asInstanceOf[TSet].valueType, forInterface) + "[]"
      case _: TOption => this.typeToNative(t.asInstanceOf[TOption].valueType, forInterface) + "?"
      case _: TMap => "{[key: string]: " + this.typeToNative(t.asInstanceOf[TMap].valueType, forInterface) + "}"
      case _ => if (forInterface) { "I" + t.name } else { t.name }
    }
  }

  protected def typeToObject(name: String, t: TypeId): String = {
    t match {
      case Primitive.TInt8 => name
      case Primitive.TInt16 => name
      case Primitive.TInt32 => name
      case Primitive.TInt64 => name
      case Primitive.TBool => name
      case Primitive.TDate => name
      case Primitive.TDouble => name
      case Primitive.TFloat => name
      case Primitive.TString => name
      case Primitive.TTime => name + ".toString()"
      case Primitive.TTs => name
      case Primitive.TTsTz => name
      case Primitive.TUUID => name
      case _: TList => name + s".map(v => ${this.typeToObject("v", t.asInstanceOf[TList].valueType)})"
      // TODO We must add something kind of substitute for a set here
      case _: TSet => name
      case _: TMap => name

      case _: TOption => name + " ? " + this.typeToObject(name, t.asInstanceOf[TOption].valueType) + " : undefined"
      case _ => name + ".toObject()"
    }
  }

  protected def isTypeCustom(t: TypeId): Boolean = {
    t match {
      case Primitive.TInt8 => false
      case Primitive.TInt16 => false
      case Primitive.TInt32 => false
      case Primitive.TInt64 => false
      case Primitive.TBool => false
      case Primitive.TDate => false
      case Primitive.TDouble => false
      case Primitive.TFloat => false
      case Primitive.TString => false
      case Primitive.TTime => false
      case Primitive.TTs => false
      case Primitive.TTsTz => false
      case Primitive.TUUID => false
      case _: TList => this.isTypeCustom(t.asInstanceOf[TList].valueType)
      case _: TSet => this.isTypeCustom(t.asInstanceOf[TSet].valueType)
      case _: TMap => this.isTypeCustom(t.asInstanceOf[TMap].valueType)
      case _: TOption => this.isTypeCustom(t.asInstanceOf[TOption].valueType)
      case _ => true
    }
  }

  protected def renderAlias(i: Alias): Seq[String] = {
    Seq(s"export type ${i.id.name} = ${this.typeToNative(i.target)};")
  }

  protected def collectAliasImports(i: Alias): Seq[TypeId] = {
    if (this.isTypeCustom(i.target)) {
      Seq(i.target)
    } else {
      Seq.empty
    }
  }

  protected def renderIdentifier(i: Identifier): Seq[String] = {
    Seq(
      s"export interface I${i.id.name} {"
    ) ++
    i.fields.map(f => s"    ${f.name}: ${this.typeToNative(f.typeId, true)};") ++
    Seq(
      s"}",
      s"",
      s"export class ${i.id.name} {"
    ) ++
    i.fields.map(f => s"    public ${f.name}: ${this.typeToNative(f.typeId)};") ++
    Seq(
      s"",
      s"    constructor(data: ${i.id.name} | I${i.id.name}) {",
      s"        if (!data) {",
      s"            return;",
      s"        }",
      s"        data = data instanceof ${i.id.name} ? data.toObject() : data;"
    ) ++
    i.fields.map(f => s"        this.${f.name} = data.${f.name};") ++
    Seq(
      s"    }",
      s"",
      s"    public toObject(): I${i.id.name} {",
      s"        return {"
    ) ++
    i.fields.map(f => s"            ${f.name}: ${this.typeToObject("this." + f.name, f.typeId)},") ++
    Seq(
      s"        }",
      s"    }",
      s"",
      s"    toString(): string {",
      s"        // TODO Must have escaping on the field value! Sync across all languages to use the same algo",
      s"        const suffix = ${i.fields.map(f => s"encodeURI(this.${f.name})").mkString(" + \":\" + ")};",
      s"        return ${"\"" + i.id.name + "#\""} + suffix;",
      s"    }",
      s"}"
    )
  }

  protected def collectIdentifierImports(i: Identifier): Seq[TypeId] = {
    i.fields.filter(f => this.isTypeCustom(f.typeId)).map(f => f.typeId)
  }

  protected def renderInterface(i: Interface): Seq[String] = {
    val extendsInterfaces =
      if (i.interfaces.length > 0) {
        "extends " + i.interfaces.map(i => "I" + i.name).mkString(", ") + " "
      } else {
        ""
      }

    Seq(
      s"export interface I${i.id.name} ${extendsInterfaces}{"
    ) ++
    i.fields.map(f => s"    ${f.name}: ${this.typeToNative(f.typeId, true)};") ++
    Seq(
      s"}"
    )
  }

  protected def collectInterfaceImports(i: Interface): Seq[TypeId] = {
    i.fields.filter(f => this.isTypeCustom(f.typeId)).map(f => f.typeId) ++
    i.interfaces.map(inf => InterfaceId(inf.pkg, inf.name))
  }

  protected def renderDto(i: DTO): Seq[String] = {
    val interfacesFields = typespace.enumFields(i.interfaces)
    val distinctFields = interfacesFields.map(_.field).filterNot(interfacesFields.contains)

    Seq(
      s"export interface I${i.id.name} {"
    ) ++
    distinctFields.map(f => s"    ${f.name}: ${this.typeToNative(f.typeId, true)};") ++
    Seq(
      s"}",
      s"",
      s"export class ${i.id.name} implements ${i.interfaces.map(inf => "I" + inf.name).mkString(", ")} {"
    ) ++
    distinctFields.map(f => s"    public ${f.name}: ${this.typeToNative(f.typeId, true)};") ++
    Seq(
      s"",
      s"    constructor(data: ${i.id.name} | I${i.id.name}) {",
      s"        if (!data) {",
      s"            return;",
      s"        }",
      s"        data = data instanceof ${i.id.name} ? data.toObject() : data;"
    ) ++
    distinctFields.map(f => s"        this.${f.name} = data.${f.name};") ++
    Seq(
      s"    }",
      s"",
      s"    public toObject(): I${i.id.name} {",
      s"        return {"
    ) ++
    distinctFields.map(f => s"            ${f.name}: ${this.typeToObject("this." + f.name, f.typeId)},") ++
    Seq(
      s"        }",
      s"    }"
    ) ++
    i.interfaces.flatMap(inf => Seq(
      s"    public toI${inf.name}(): I${inf.name} {",
      s"        return {"
      ) ++
      typespace.enumFields(typespace(inf))
        .map(inff => s"            ${inff.field.name}: ${this.typeToObject("this." + inff.field.name, inff.field.typeId)},") ++
      Seq(
        s"        }",
        s"    }"
      )
    ) ++
    Seq(
      s"}"
    )
  }

  protected def collectDtoImports(i: DTO): Seq[TypeId] = {
    val interfacesFields = typespace.enumFields(i.interfaces)
    val distinctFields = interfacesFields.map(_.field).filterNot(interfacesFields.contains)
    distinctFields.filter(f => this.isTypeCustom(f.typeId)).map(f => f.typeId) ++
    i.interfaces.map(inf => inf)
  }

  def renderAdt(i: Adt): Seq[String] = {
    Seq(s"adt ${i.id.name}")
  }

  protected def renderServiceMethod(method: ILAst.Service.DefMethod): Seq[String] = {
    Seq(
      s"    public ${method.toString}(): Something {",
      s"    }"
    )
  }

  protected def renderService(i: Service): Seq[String] = {
    Seq(
      s"export class ${i.id.name}Service {",
      s"    constructor() {",
      s"        // TODO Fill in with transport",
      s"    }",
      s""
    ) ++
      i.methods.map(m => this.renderServiceMethod(m)).flatten ++
      Seq(
        s"}"
      )
  }
}