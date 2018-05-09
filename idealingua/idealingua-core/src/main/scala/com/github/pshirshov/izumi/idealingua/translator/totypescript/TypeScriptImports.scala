package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Package, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.Output.{Algebraic, Singular, Struct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Service, TypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

final case class TypeScriptImport(id: TypeId, pkg: String)

final case class TypeScriptImports(imports: List[TypeScriptImport] = List.empty) {
  private def renderTypeImports(id: TypeId, ts: Typespace): String = id match {
    case adt: AdtId => s"${adt.name}, ${adt.name}Helpers"
    case i: InterfaceId => s"${i.name}, ${ts.implId(i).name}, ${ts.implId(i).name}Serialized"
    case d: DTOId => s"${d.name}, ${d.name}Serialized"
    case _ => id.name
  }

  def render(ts: Typespace): String = {
    if (imports.isEmpty) {
      return ""
    }

    imports.filterNot(_.id.isInstanceOf[AliasId]).groupBy(_.pkg)
      .map(i => "import {" + (if (i._2.length > 1) "\n" else " ") + i._2.map(i2 => (if (i._2.length > 1) "    " else "") + renderTypeImports(i2.id, ts))
      .mkString(if (i._2.length > 1) ",\n" else "") + (if (i._2.length > 1) "\n" else " ") + s"} from '${i._1}';").mkString("\n")
  }

  def findImport(id: TypeId): Option[TypeScriptImport] = {
    return imports.find(i => i.id == id)
  }
}

object TypeScriptImports {
  def apply(imports: List[TypeScriptImport]): TypeScriptImports =
    new TypeScriptImports(imports)

  def apply(ts: Typespace, definition: TypeDef, fromPkg: Package, extra: List[TypeScriptImport] = List.empty): TypeScriptImports =
    TypeScriptImports(fromDefinition(ts, definition, fromPkg, extra))

  def apply(ts: Typespace, i: Service, fromPkg: Package, extra: List[TypeScriptImport]): TypeScriptImports =
    TypeScriptImports(fromService(ts, i, fromPkg, extra))

  protected def withImport(t: TypeId, fromPackage: Package): Seq[String] = {
    t match {
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
      // On the same level, let's just import the type file
      return Seq(s"./${t.name}")
    }

    var importOffset = ""
    var importFile = ""

    if (t.path.toPackage.mkString(".").startsWith(fromPackage.mkString("."))) {
      importFile = "./" + t.path.toPackage.mkString(".").substring(fromPackage.mkString(".").length + 1)
    } else {
      (1 to (t.path.toPackage.size - nestedDepth)).foreach(_ => importOffset += "../")
      importFile = importOffset + t.path.toPackage.drop(nestedDepth).mkString("/")//+ "/" + t.name
    }

    Seq(importFile)
  }

  protected def fromTypes(types: List[TypeId], fromPkg: Package, extra: List[TypeScriptImport] = List.empty): List[TypeScriptImport] = {
    val imports = types.distinct
    if (fromPkg.isEmpty) {
      return List.empty
    }

    imports.flatMap( i => this.withImport(i, fromPkg).map(wi => (i, wi))).filterNot(_._2.isEmpty).map(m => TypeScriptImport(m._1, m._2)) ++ extra
  }

  protected def collectTypes(ts: Typespace, id: TypeId): List[TypeId] = id match {
    case p: Primitive => List(p)
    case g: Generic => g match {
      case gm: Generic.TMap => List(gm) ++ collectTypes(ts, gm.valueType)
      case gl: Generic.TList => List(gl) ++ collectTypes(ts, gl.valueType)
      case gs: Generic.TSet => List(gs) ++ collectTypes(ts, gs.valueType)
      case go: Generic.TOption => List(go) ++ collectTypes(ts, go.valueType)
    }
    case a: AdtId => List(a)
    case i: InterfaceId => List(i)
    case _: AliasId => List(ts(id).asInstanceOf[Alias].target)
    case id: IdentifierId => List(id)
    case e: EnumId => List(e)
    case dto: DTOId => List(dto)
    case _ => throw new IDLException(s"Impossible type in collectTypes ${id.name} ${id.path.toPackage.mkString(".")}")
  }

  protected def collectTypes(ts: Typespace, definition: TypeDef): List[TypeId] = definition match {
    case i: Alias =>
      List(i.target)
    case _: Enumeration =>
      List.empty
    case i: Identifier =>
      i.fields.flatMap(f => List(f.typeId) ++ collectTypes(ts, f.typeId))
    case i: Interface =>
      i.struct.superclasses.interfaces ++ ts.structure.structure(i).all.flatMap(f => List(f.field.typeId) ++ collectTypes(ts, f.field.typeId)) ++
          ts.inheritance.allParents(i.id).filterNot(i.struct.superclasses.interfaces.contains).filterNot(ff => ff == i.id).map(ifc => ts.implId(ifc))
    case d: DTO =>
      d.struct.superclasses.interfaces ++ ts.structure.structure(d).all.flatMap(f => List(f.field.typeId) ++ collectTypes(ts, f.field.typeId)) ++
        ts.inheritance.allParents(d.id).filterNot(d.struct.superclasses.interfaces.contains).map(ifc => ts.implId(ifc))
    case a: Adt =>
      a.alternatives.flatMap(al => List(al.typeId) ++ collectTypes(ts, al.typeId))
  }

  protected def fromDefinition(ts: Typespace, definition: TypeDef, fromPkg: Package, extra: List[TypeScriptImport] = List.empty): List[TypeScriptImport] = {
    val types = collectTypes(ts, definition)
    fromTypes(types, fromPkg, extra)
  }

  protected def fromService(ts: Typespace, svc: Service, fromPkg: Package, extra: List[TypeScriptImport] = List.empty): List[TypeScriptImport] = {
    val types = svc.methods.flatMap {
      case m: RPCMethod => m.signature.input.fields.map(f => f.typeId) ++ (m.signature.output match {
        case st: Struct => st.struct.fields.flatMap(ff => collectTypes(ts, ff.typeId))
        case ad: Algebraic => ad.alternatives.flatMap(al => collectTypes(ts, al.typeId))
        case si: Singular => collectTypes(ts, si.typeId)
      })
    }

    fromTypes(types, fromPkg, extra)
  }
}
