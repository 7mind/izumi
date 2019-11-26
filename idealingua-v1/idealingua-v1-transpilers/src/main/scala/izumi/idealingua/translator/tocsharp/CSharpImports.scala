package izumi.idealingua.translator.tocsharp

import izumi.fundamentals.platform.language.Quirks
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common.{Generic, Package, Primitive, TypeId}
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.DefMethod.Output.{Algebraic, Alternative, Singular, Struct, Void}
import izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.il.ast.typed.{Buzzer, DefMethod, Service, TypeDef}
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.tocsharp.types.CSharpType

final case class CSharpImport(id: TypeId, namespace: Seq[String], usingName: String)

final case class CSharpImports(imports: List[CSharpImport] = List.empty) {
  protected def isAmbiguousName(name: String): Boolean = {
    val ambiguous = Seq("Type", "Environment")
    ambiguous.contains(name)
  }

  def renderImports(extra: List[String] = List.empty): String = {
    if (imports.isEmpty && extra.isEmpty) {
      return ""
    }

    val usings = imports.flatMap(i => if (i.namespace.nonEmpty) i.namespace.map(n => s"using $n;") else List("")) ++
      extra.map(e => s"using $e;")
    usings.distinct.mkString("\n")
  }

  def renderUsings(): String = {
    if (imports.isEmpty) {
      return ""
    }

    imports.map(i => if (i.usingName != "") s"using ${i.usingName} = ${i.id.path.toPackage.map(p => p.capitalize).mkString(".")}.${i.id.name};" else "").mkString("\n")
  }

  def findImport(id: TypeId): Option[CSharpImport] = {
    imports.find(i => i.id == id)
  }

  def withImport(id: TypeId): String = {
    if (isAmbiguousName(id.name)) {
      id.path.toPackage.map(p => p.capitalize).mkString(".") + "." + id.name
    } else {
      val rec = findImport(id)
      if (rec.isDefined && rec.get.usingName != "") {
        rec.get.usingName
      } else {
        id.name
      }
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

  def apply(i: Buzzer, fromPkg: Package, extra: List[CSharpImport])(implicit ts: Typespace): CSharpImports =
    CSharpImports(fromBuzzer(ts, i, fromPkg, extra))

  protected def withImport(t: TypeId, fromPackage: Package, forTest: Boolean = false): Seq[String] = {
    Quirks.discard(forTest)
    t match {
      case Primitive.TTime => return Seq("System")
      case Primitive.TTs => return Seq("System", "IRT", "System.Globalization")
      case Primitive.TTsTz => return Seq("System", "IRT", "System.Globalization")
      case Primitive.TDate => return Seq("System")
      case Primitive.TUUID => return Seq("System")
      case Primitive.TBLOB => ???
      case g: Generic => g match {
        case _: Generic.TOption => return Seq("System")
        case _: Generic.TMap => return Seq("System.Collections", "System.Collections.Generic")
        case _: Generic.TList => return Seq("System.Collections", "System.Collections.Generic")
        case _: Generic.TSet => return Seq("System.Collections", "System.Collections.Generic")
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

    Seq(t.path.toPackage.map(p => p.capitalize).mkString("."))
  }

  protected def fromTypes(types: List[TypeId], fromPkg: Package, extra: List[CSharpImport] = List.empty): List[CSharpImport] = {
    val imports = types.distinct
    if (fromPkg.isEmpty) {
      return List.empty
    }

    val packages = imports.map(i => (i, this.withImport(i, fromPkg))).filterNot(_._2.isEmpty).groupBy(_._1.name)

    // For each type, which has more of the same names in the current module, we need to add a unique number of
    // import so it can be distinctly used in the types reference
    packages.flatMap(pt =>
      if (pt._2.length == 1 || pt._2.head._1.isInstanceOf[Generic])
        Seq(CSharpImport(pt._2.head._1, pt._2.head._2, s""))
      else
        pt._2.zipWithIndex.map { case (pt2, index) =>
          CSharpImport(pt2._1, pt2._2, s"${pt2._1.name}_$index")
        }
    ).toList ++ extra
  }

  protected def collectTypes(ts: Typespace, id: TypeId): List[TypeId] = id match {
    case p: Primitive => List(p)
    case g: Generic => g match {
      case gm: Generic.TMap => List(gm) ++ collectTypes(ts, gm.valueType)
      case gl: Generic.TList => List(gl) ++ collectTypes(ts, gl.valueType)
      case gs: Generic.TSet => List(gs) ++ collectTypes(ts, gs.valueType)
      case go: Generic.TOption => (if (CSharpType(go.valueType)(im = null, ts).isNullable) List(go) else List.empty) ++ collectTypes(ts, go.valueType)
    }
    case a: AdtId => List(a)
    case i: InterfaceId => List(i)
    case _: AliasId => collectTypes(ts, ts.dealias(id))
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
      ts.structure.structure(i).all.flatMap(f => List(f.field.typeId) ++ collectTypes(ts, f.field.typeId)).filterNot(_ == definition.id)
    case d: DTO =>
      d.struct.superclasses.interfaces ++
      ts.structure.structure(d).all.flatMap(f => List(f.field.typeId) ++ collectTypes(ts, f.field.typeId)).filterNot(_ == definition.id)
    case a: Adt =>
      a.alternatives.flatMap(al => List(al.typeId) ++ collectTypes(ts, al.typeId))
  }

  protected def fromDefinition(ts: Typespace, definition: TypeDef, fromPkg: Package, extra: List[CSharpImport] = List.empty): List[CSharpImport] = {
    val types = collectTypes(ts, definition)
    fromTypes(types, fromPkg, extra)
  }

  protected def fromRPCMethodOutput(ts: Typespace, output: DefMethod.Output): List[TypeId] = {
    output match {
      case st: Struct => st.struct.fields.flatMap(ff => collectTypes(ts, ff.typeId))
      case ad: Algebraic => ad.alternatives.flatMap(al => collectTypes(ts, al.typeId))
      case si: Singular => collectTypes(ts, si.typeId)
      case _: Void => List.empty
      case al: Alternative => fromRPCMethodOutput(ts, al.success) ++ fromRPCMethodOutput(ts, al.failure)
    }
  }

  protected def fromService(ts: Typespace, svc: Service, fromPkg: Package, extra: List[CSharpImport] = List.empty): List[CSharpImport] = {
    val types = svc.methods.flatMap {
      case m: RPCMethod => m.signature.input.fields.flatMap(f => collectTypes(ts, f.typeId)) ++ fromRPCMethodOutput(ts, m.signature.output)
    }

    fromTypes(types, fromPkg, extra)
  }

  protected def fromBuzzer(ts: Typespace, i: Buzzer, fromPkg: Package, extra: List[CSharpImport] = List.empty): List[CSharpImport] = {
    val types = i.events.flatMap {
      case m: RPCMethod => m.signature.input.fields.flatMap(f => collectTypes(ts, f.typeId)) ++ fromRPCMethodOutput(ts, m.signature.output)
    }

    fromTypes(types, fromPkg, extra)
  }
}
