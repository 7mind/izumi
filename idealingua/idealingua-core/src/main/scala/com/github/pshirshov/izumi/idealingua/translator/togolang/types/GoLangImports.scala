package com.github.pshirshov.izumi.idealingua.translator.togolang.types

import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, Generic, Package, Primitive, TypeId}
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.Output.{Algebraic, Singular, Struct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Service, TypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._

case class GoLangImports(imports: List[GoLangImportRecord] = List.empty) {
  def renderImports(extra: Seq[String] = List.empty): String = {
    // Exclude extra ones which are already included into the import
    val combined = imports.map(i => i.renderImport()) ++ extra.filterNot(e => imports.exists(p => p.pkg.mkString(".") == e)).map(e => "\"" + e + "\"")

    if (combined.isEmpty) {
      return ""
    }

    s"""import (
       |${combined.mkString("\n").shift(4)}
       |)
     """.stripMargin
  }

  def findImport(id: TypeId): Option[GoLangImportRecord] = {
    return imports.find(i => i.id == id)
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
  def apply(imports: List[GoLangImportRecord]): GoLangImports =
    new GoLangImports(imports)

  def apply(definition: TypeDef, fromPkg: Package, extra: List[GoLangImportRecord] = List.empty): GoLangImports =
    GoLangImports(fromDefinition(definition, fromPkg, extra))

  def apply(i: Service, fromPkg: Package, extra: List[GoLangImportRecord]): GoLangImports =
    GoLangImports(fromService(i, fromPkg, extra))

  protected def withImport(t: TypeId, fromPackage: Package): Seq[Seq[String]] = {
    t match {
      case Primitive.TTime => return Seq(Seq("time"), Seq("strings"), Seq("strconv"))
      case Primitive.TTs => return Seq(Seq("time"), Seq("strings"), Seq("strconv"))
      case Primitive.TTsTz => return Seq(Seq("time"), Seq("strings"), Seq("strconv"))
      case Primitive.TDate => return Seq(Seq("time"), Seq("strings"), Seq("strconv"))
      case Primitive.TUUID => return Seq(Seq("regexp"))
      case g: Generic => g match {
        case go: Generic.TOption => return Seq.empty
        case gm: Generic.TMap => return Seq.empty
        case gl: Generic.TList => return Seq.empty
        case gs: Generic.TSet => return Seq.empty
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

    return Seq(t.path.toPackage)
  }

  protected def fromTypes(types: List[TypeId], fromPkg: Package, extra: List[GoLangImportRecord] = List.empty): List[GoLangImportRecord] = {
    val imports = types.distinct
    if (fromPkg.isEmpty) {
      return List.empty
    }

    val packages = imports.flatMap( i =>  this.withImport(i, fromPkg).map(wi => (i, Some(None), wi))).filterNot(_._3.isEmpty)
      // We might get duplicate packages due to time used for multiple primitives, etc. We need to further reduce the list
      .groupBy(_._3.mkString(".")).map(_._2.head).toList

    // For each type, which package ends with the same path, we need to add a unique number of import so it can be
    // distinctly used in the types reference
    packages.zipWithIndex.map{ case (tp, index) =>
      if (packages.exists(p => p._3.last == tp._3.last && p._1 != tp._1))
        GoLangImportRecord(tp._1, s"imp_$index", tp._3)
      else
        GoLangImportRecord(tp._1, tp._3.last, tp._3)
    } ++ extra
  }

  protected def collectTypes(id: TypeId): List[TypeId] = id match {
    case p: Primitive => List(p)
    case g: Generic => g match {
      case gm: Generic.TMap => List(gm) ++ collectTypes(gm.valueType)
      case gl: Generic.TList => List(gl) ++ collectTypes(gl.valueType)
      case gs: Generic.TSet => List(gs) ++ collectTypes(gs.valueType)
      case go: Generic.TOption => List(go) ++ collectTypes(go.valueType)
    }
    case a: AdtId => List(a)
    case i: InterfaceId => List(i)
    case al: AliasId => List(al)
    case id: IdentifierId => List(id)
    case e: EnumId => List(e)
    case dto: DTOId => List(dto)
    case _ => throw new IDLException(s"Impossible type in collectTypes ${id.name} ${id.path.toPackage.mkString(".")}")
  }

  protected def collectTypes(definition: TypeDef): List[TypeId] = definition match {
    case i: Alias =>
      List(i.target)
    case _: Enumeration =>
      List.empty
    case i: Identifier =>
      i.fields.flatMap(f => List(f.typeId) ++ collectTypes(f.typeId))
    case i: Interface =>
      i.struct.superclasses.all ++ i.struct.fields.flatMap(f => List(f.typeId) ++ collectTypes(f.typeId))
    case d: DTO =>
      d.struct.superclasses.all ++ d.struct.fields.flatMap(f => List(f.typeId) ++ collectTypes(f.typeId))
    case a: Adt =>
      a.alternatives.flatMap(al => List(al.typeId) ++ collectTypes(al.typeId))
    case _: Builtin =>
      throw new IDLException(s"Impossible case: User type expected ${definition.id.name}")
  }

  protected def fromDefinition(definition: TypeDef, fromPkg: Package, extra: List[GoLangImportRecord] = List.empty): List[GoLangImportRecord] = {
    val types = collectTypes(definition)
    fromTypes(types, fromPkg, extra)
  }

  protected def fromService(svc: Service, fromPkg: Package, extra: List[GoLangImportRecord] = List.empty): List[GoLangImportRecord] = {
    val types = svc.methods.flatMap {
      case m: RPCMethod => m.signature.input.fields.map(f => f.typeId) ++ (m.signature.output match {
        case st: Struct => st.struct.fields.map(ff => ff.typeId)
        case ad: Algebraic => ad.alternatives.map(al => al.typeId)
        case si: Singular => List(si.typeId)
      })
    }

    fromTypes(types, fromPkg, extra)
  }
}