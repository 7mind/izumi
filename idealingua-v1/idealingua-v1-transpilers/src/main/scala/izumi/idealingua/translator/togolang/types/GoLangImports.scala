package izumi.idealingua.translator.togolang.types

import izumi.fundamentals.platform.language.Quirks
import izumi.idealingua.model.common.{Generic, Package, Primitive, TypeId}
import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.DefMethod.Output.{Algebraic, Alternative, Singular, Struct, Void}
import izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod
import izumi.idealingua.model.il.ast.typed.{Buzzer, DefMethod, Service, TypeDef}
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.publishing.manifests.GoLangBuildManifest
import izumi.idealingua.model.typespace.Typespace

final case class GoLangImports(imports: List[GoLangImportRecord] = List.empty, manifest: GoLangBuildManifest) {
  def renderImports(extra: Seq[String] = List.empty): String = {
    // Exclude extra ones which are already included into the import
    val prefix = GoLangBuildManifest.importPrefix(manifest)
    val combined = imports.map(i => i.renderImport(prefix)) ++ extra.filterNot(e => imports.exists(p => p.pkg.mkString(".") == e)).map(e => "\"" + e + "\"")

    if (combined.isEmpty) {
      return ""
    }

    s"""import (
       |${combined.distinct.mkString("\n").shift(4)}
       |)
     """.stripMargin
  }

  def findImport(id: TypeId): Option[GoLangImportRecord] = {
    imports.find(i => i.id == id)
  }

  def withImport(id: TypeId): String = {
    val rec = findImport(id)
    if (rec.isDefined) {
      rec.get.importName + "."
    } else {
      ""
    }
  }
}

object GoLangImports {
  def apply(types: List[TypeId], fromPkg: Package, ts: Typespace, extra: List[GoLangImportRecord], forTest: Boolean, manifest: GoLangBuildManifest): GoLangImports = {
    Quirks.discard(ts)
    new GoLangImports(fromTypes(types, fromPkg, extra, forTest), manifest)
  }

  def apply(imports: List[GoLangImportRecord], manifest: GoLangBuildManifest): GoLangImports =
    new GoLangImports(imports, manifest)

  def apply(definition: TypeDef, fromPkg: Package, ts: Typespace, extra: List[GoLangImportRecord] = List.empty, manifest: GoLangBuildManifest): GoLangImports =
    GoLangImports(fromDefinition(definition, fromPkg, extra, ts), manifest)

  def apply(i: Service, fromPkg: Package, extra: List[GoLangImportRecord], manifest: GoLangBuildManifest): GoLangImports =
    GoLangImports(fromService(i, fromPkg, extra), manifest)

  def apply(i: Buzzer, fromPkg: Package, extra: List[GoLangImportRecord], manifest: GoLangBuildManifest): GoLangImports =
    GoLangImports(fromBuzzer(i, fromPkg, extra), manifest)

  protected def withImport(t: TypeId, fromPackage: Package, forTest: Boolean = false): Seq[Seq[String]] = {
    t match {
      case Primitive.TTime => return if (forTest) Seq(Seq("time")) else Seq(Seq("time"), Seq("irt"))
      case Primitive.TTs => return if (forTest) Seq(Seq("time")) else Seq(Seq("time"), Seq("irt"))
      case Primitive.TTsTz => return if (forTest) Seq(Seq("time")) else Seq(Seq("time"), Seq("irt"))
      case Primitive.TTsU => return if (forTest) Seq(Seq("time")) else Seq(Seq("time"), Seq("irt"))
      case Primitive.TDate => return if (forTest) Seq(Seq("time")) else Seq(Seq("time"), Seq("irt"))
      case Primitive.TUUID => return if (forTest) Seq.empty else Seq(Seq("regexp"))
      case Primitive.TBLOB => ???
      case g: Generic => g match {
        case _: Generic.TOption => return Seq.empty
        case _: Generic.TMap => return Seq.empty
        case _: Generic.TList => return Seq.empty
        case _: Generic.TSet => return Seq.empty
      }
      case _: Primitive => return Seq.empty
      case _ =>
    }

    if (t.path.toPackage.isEmpty) {
      return Seq.empty
    }

    val nestedDepth = t.path.toPackage.zip(fromPackage).count(x => x._1 == x._2)

    if (nestedDepth == t.path.toPackage.size) {
      // It seems that we don't need if namespace is the same, Go should handle resolution itself
      return Seq.empty
    }

    Seq(t.path.toPackage)
  }

  protected def fromTypes(types: List[TypeId], fromPkg: Package, extra: List[GoLangImportRecord] = List.empty, forTest: Boolean = false): List[GoLangImportRecord] = {
    val imports = types.distinct
    if (fromPkg.isEmpty) {
      return List.empty
    }

    val packages = imports.flatMap( i =>  this.withImport(i, fromPkg, forTest).map(wi => (i, Some(None), wi))).filterNot(_._3.isEmpty)
      // We might get duplicate packages due to time used for multiple primitives, etc. We need to further reduce the list
      .groupBy(_._3.mkString(".")).flatMap(_._2).toList


    // For each type, which package ends with the same path, we need to add a unique number of import so it can be
    // distinctly used in the types reference. Make sure that the last one is the same and that domain itself is different
    packages.zipWithIndex.map{ case (tp, index) =>
      if (packages.exists(p => p._3.last == tp._3.last && p._1 != tp._1 && p._3.mkString(".") != tp._3.mkString(".")))
        GoLangImportRecord(tp._1, s"imp_$index", tp._3)
      else
        GoLangImportRecord(tp._1, tp._3.last, tp._3)
    } ++ extra
  }

  def collectTypes(id: TypeId, fieldNested: Boolean = false): List[TypeId] = {
    if (fieldNested) {
      if (id == Primitive.TUUID) {
        return List.empty
      }
    }

    id match {
      case p: Primitive => List(p)
      case g: Generic => g match {
        case gm: Generic.TMap => List(gm) ++ collectTypes(gm.valueType, fieldNested = true)
        case gl: Generic.TList => List(gl) ++ collectTypes(gl.valueType, fieldNested = true)
        case gs: Generic.TSet => List(gs) ++ collectTypes(gs.valueType, fieldNested = true)
        case go: Generic.TOption => List(go) ++ collectTypes(go.valueType, fieldNested = true)
      }
      case a: AdtId => List(a)
      case i: InterfaceId => List(i)
      case al: AliasId => List(al)
      case id: IdentifierId => List(id)
      case e: EnumId => List(e)
      case dto: DTOId => List(dto)
      case _ => throw new IDLException(s"Impossible type in collectTypes ${id.name} ${id.path.toPackage.mkString(".")}")
    }
  }

  protected def collectTypes(definition: TypeDef, ts: Typespace): List[TypeId] = definition match {
    case i: Alias =>
      List(i.target)
    case _: Enumeration =>
      List.empty
    case i: Identifier =>
      i.fields.flatMap(f => List(f.typeId) ++ collectTypes(f.typeId))
    case i: Interface =>
      i.struct.superclasses.interfaces ++ ts.structure.structure(i).all.flatMap(f => List(f.field.typeId) ++ collectTypes(f.field.typeId, fieldNested = true))
    case d: DTO =>
      d.struct.superclasses.interfaces ++ ts.structure.structure(d).all.flatMap(f => List(f.field.typeId) ++ collectTypes(f.field.typeId, fieldNested = true))
    case a: Adt =>
      a.alternatives.flatMap(al => List(al.typeId) ++ collectTypes(al.typeId))
  }

  protected def fromDefinition(definition: TypeDef, fromPkg: Package, extra: List[GoLangImportRecord] = List.empty, ts: Typespace = null): List[GoLangImportRecord] = {
    val types = collectTypes(definition, ts)
    fromTypes(types, fromPkg, extra)
  }

  protected def fromRPCMethodOutput(output: DefMethod.Output): List[TypeId] = {
    output match {
      case st: Struct => st.struct.fields.map(ff => ff.typeId)
      case ad: Algebraic => ad.alternatives.map(al => al.typeId)
      case si: Singular => List(si.typeId)
      case _: Void => List.empty
      case al: Alternative => fromRPCMethodOutput(al.success) ++ fromRPCMethodOutput(al.failure)
    }
  }

  protected def fromService(svc: Service, fromPkg: Package, extra: List[GoLangImportRecord] = List.empty): List[GoLangImportRecord] = {
    val types = svc.methods.flatMap {
      case m: RPCMethod => m.signature.input.fields.map(f => f.typeId) ++ fromRPCMethodOutput(m.signature.output)
    }

    fromTypes(types, fromPkg, extra)
  }

  protected def fromBuzzer(i: Buzzer, fromPkg: Package, extra: List[GoLangImportRecord] = List.empty): List[GoLangImportRecord] = {
    val types = i.events.flatMap {
      case m: RPCMethod => m.signature.input.fields.map(f => f.typeId) ++ fromRPCMethodOutput(m.signature.output)
    }

    fromTypes(types, fromPkg, extra)
  }
}
