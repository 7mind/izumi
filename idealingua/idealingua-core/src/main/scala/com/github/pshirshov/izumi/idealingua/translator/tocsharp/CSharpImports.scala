package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Package, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.Output.{Algebraic, Singular, Struct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Service, TypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

final case class CSharpImport(id: TypeId, namespaces: Seq[String], usingName: String)

final case class CSharpImports(imports: List[CSharpImport] = List.empty) {
  private def renderTypeImports(id: TypeId, ts: Typespace): String = id match {
    case adt: AdtId => s"${adt.name}, ${adt.name}Helpers"
    case i: InterfaceId => s"${i.name}, ${i.name + ts.implId(i).name}, ${i.name + ts.implId(i).name}Serialized"
    case d: DTOId => {
      val mirrorInterface = ts.sourceId(d)
      if (mirrorInterface.isDefined) {
        s"${mirrorInterface.get.name + d.name}, ${mirrorInterface.get.name + d.name}Serialized"
      } else {
        s"${d.name}, ${d.name}Serialized"
      }
    }

    case _ => id.name
  }

  def render(ts: Typespace): String = {
    if (imports.isEmpty) {
      return ""
    }

    return "// Import not implemnented"
//
//    imports.filterNot(_.id.isInstanceOf[AliasId]).groupBy(_.namespaces)
//      .map(i => "import {" + (if (i._2.length > 1) "\n" else " ") + i._2.map(i2 => (if (i._2.length > 1) "    " else "") + renderTypeImports(i2.id, ts))
//      .mkString(if (i._2.length > 1) ",\n" else "") + (if (i._2.length > 1) "\n" else " ") + s"} from '${i._1}';").mkString("\n")
  }

  def findImport(id: TypeId): Option[CSharpImport] = {
    return imports.find(i => i.id == id)
  }

  def withImport(id: TypeId): String = {
    val rec = findImport(id)
    if (rec.isDefined) {
      rec.get.usingName + "."
    } else {
      ""
    }
  }
}

object CSharpImports {
  def apply(imports: List[CSharpImport]): CSharpImports =
    new CSharpImports(imports)

  def apply(definition: TypeDef, fromPkg: Package, extra: List[CSharpImport] = List.empty)(implicit ts: Typespace): CSharpImports =
    CSharpImports(fromDefinition(ts, definition, fromPkg, extra))

  def apply(i: Service, fromPkg: Package, extra: List[CSharpImport])(implicit ts: Typespace): CSharpImports =
    CSharpImports(fromService(ts, i, fromPkg, extra))

  protected def withImport(t: TypeId, fromPackage: Package, forTest: Boolean = false): Seq[Seq[String]] = {
//    t match {
//      case Primitive.TTime => return if (forTest) Seq(Seq("time")) else Seq(Seq("time"), Seq("strings"), Seq("strconv"))
//      case Primitive.TTs => return Seq(Seq("time"))
//      case Primitive.TTsTz => return Seq(Seq("time"))
//      case Primitive.TDate => return if (forTest) Seq(Seq("time")) else Seq(Seq("time"), Seq("strings"), Seq("strconv"))
//      case Primitive.TUUID => return if (forTest) Seq.empty else Seq(Seq("regexp"))
//      case g: Generic => g match {
//        case _: Generic.TOption => return Seq.empty
//        case _: Generic.TMap => return Seq.empty
//        case _: Generic.TList => return Seq.empty
//        case _: Generic.TSet => return Seq.empty
//      }
//      case _: Primitive => return Seq.empty
//      case _ =>
//    }

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

  protected def fromTypes(types: List[TypeId], fromPkg: Package, extra: List[CSharpImport] = List.empty): List[CSharpImport] = {
    val imports = types.distinct
    if (fromPkg.isEmpty) {
      return List.empty
    }

    val packages = imports.flatMap( i =>  this.withImport(i, fromPkg).map(wi => (i, Some(None), wi))).filterNot(_._3.isEmpty)
      .groupBy(_._1.name).map(_._2.head).toList

    // For each type, which package ends with the same path, we need to add a unique number of import so it can be
    // distinctly used in the types reference
    packages.zipWithIndex.map{ case (tp, index) =>
      if (packages.exists(p => p._3.last == tp._3.last && p._1 != tp._1))
        CSharpImport(tp._1, tp._3, s"imp_$index")
      else
        CSharpImport(tp._1, tp._3, tp._3.last)
    } ++ extra
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
      i.struct.superclasses.interfaces ++
      ts.structure.structure(i).all.flatMap(f => List(f.field.typeId) ++ collectTypes(ts, f.field.typeId)).filterNot(_ == definition.id) ++
      ts.inheritance.allParents(i.id).filterNot(i.struct.superclasses.interfaces.contains).filterNot(ff => ff == i.id).map(ifc => ts.implId(ifc))
    case d: DTO =>
      d.struct.superclasses.interfaces ++
      ts.structure.structure(d).all.flatMap(f => List(f.field.typeId) ++ collectTypes(ts, f.field.typeId)).filterNot(_ == definition.id) ++
      ts.inheritance.allParents(d.id).filterNot(d.struct.superclasses.interfaces.contains).map(ifc => ts.implId(ifc))
    case a: Adt =>
      a.alternatives.flatMap(al => List(al.typeId) ++ collectTypes(ts, al.typeId))
  }

  protected def fromDefinition(ts: Typespace, definition: TypeDef, fromPkg: Package, extra: List[CSharpImport] = List.empty): List[CSharpImport] = {
    val types = collectTypes(ts, definition)
    fromTypes(types, fromPkg, extra)
  }

  protected def fromService(ts: Typespace, svc: Service, fromPkg: Package, extra: List[CSharpImport] = List.empty): List[CSharpImport] = {
    val types = svc.methods.flatMap {
      case m: RPCMethod => m.signature.input.fields.flatMap(f => collectTypes(ts, f.typeId)) ++ (m.signature.output match {
        case st: Struct => st.struct.fields.flatMap(ff => collectTypes(ts, ff.typeId))
        case ad: Algebraic => ad.alternatives.flatMap(al => collectTypes(ts, al.typeId))
        case si: Singular => collectTypes(ts, si.typeId)
      })
    }

    fromTypes(types, fromPkg, extra)
  }
}
