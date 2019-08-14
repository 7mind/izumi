package izumi.idealingua.translator.totypescript

import izumi.idealingua.model.common.{Generic, Package, Primitive, TypeId}
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.DefMethod.Output.{Algebraic, Alternative, Singular, Struct, Void}
import izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod
import izumi.idealingua.model.il.ast.typed.{Buzzer, DefMethod, Service, TypeDef}
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.publishing.manifests.{TypeScriptBuildManifest, TypeScriptProjectLayout}
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.totypescript.layout.TypescriptNamingConvention

import scala.util.Try
import scala.util.control.Breaks._

final case class TypeScriptImport(id: TypeId, pkg: String)

final case class TypeScriptImports(imports: List[TypeScriptImport] = List.empty, manifest: TypeScriptBuildManifest) {
  private def renderTypeImports(id: TypeId, ts: Typespace): String = id match {
    case adt: AdtId => s"${adt.name}, ${adt.name}Serialized, ${adt.name}Helpers"
    case i: IdentifierId => s"${i.name}"
    case i: InterfaceId => s"${i.name}, ${i.name + ts.tools.implId(i).name}, ${i.name + ts.tools.implId(i).name}Serialized"
    case d: DTOId => {
      val mirrorInterface = ts.tools.sourceId(d)
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

    imports.filterNot(_.id.isInstanceOf[AliasId]).groupBy(_.pkg)
      .map(i => if (i._1.startsWith("import")) i._1 else
              "import {\n" + i._2.map(i2 => renderTypeImports(i2.id, ts)).mkString(",").split(',').map(i2 => i2.trim).distinct.map(i2 => "    "  + i2)
            .mkString(",\n") + s"\n} from '${i._1}';")
      .mkString("\n")
  }

  def findImport(id: TypeId): Option[TypeScriptImport] = {
    imports.find(i => i.id == id)
  }
}

object TypeScriptImports {
  def apply(imports: List[TypeScriptImport], manifest: TypeScriptBuildManifest): TypeScriptImports =
    new TypeScriptImports(imports, manifest)

  def apply(ts: Typespace, definition: TypeDef, fromPkg: Package, extra: List[TypeScriptImport] = List.empty, manifest: TypeScriptBuildManifest): TypeScriptImports =
    TypeScriptImports(fromDefinition(ts, definition, fromPkg, extra, manifest), manifest)

  def apply(ts: Typespace, i: Service, fromPkg: Package, extra: List[TypeScriptImport], manifest: TypeScriptBuildManifest): TypeScriptImports =
    TypeScriptImports(fromService(ts, i, fromPkg, extra, manifest), manifest)

  def apply(ts: Typespace, i: Buzzer, fromPkg: Package, extra: List[TypeScriptImport], manifest: TypeScriptBuildManifest): TypeScriptImports =
    TypeScriptImports(fromBuzzer(ts, i, fromPkg, extra, manifest), manifest)

  protected def withImport(t: TypeId, fromPackage: Package, manifest: TypeScriptBuildManifest): Seq[String] = {
    val depth: Int = if(manifest.layout == TypeScriptProjectLayout.YARN) {
      1
    } else {
      fromPackage.size
    }

    val pathToRoot = List.fill(depth)("../").mkString
    val scopeRoot = if (manifest.layout == TypeScriptProjectLayout.YARN) {
      manifest.yarn.scope + "/"
    } else {
      pathToRoot
    }

    t match {
      case g: Generic => g match {
        case _: Generic.TOption => return Seq.empty
        case _: Generic.TMap => return Seq.empty
        case _: Generic.TList => return Seq.empty
        case _: Generic.TSet => return Seq.empty
      }
      case p: Primitive => p match {
        case Primitive.TTs => return Seq(s"import { Formatter } from '${scopeRoot}irt';")
        case Primitive.TTsTz => return Seq(s"import { Formatter } from '${scopeRoot}irt';")
        case Primitive.TTime => return Seq(s"import { Formatter } from '${scopeRoot}irt';")
        case Primitive.TDate => return Seq(s"import { Formatter } from '${scopeRoot}irt';")
        case _ => return Seq.empty
      }
      case _ =>
    }

    if (t.path.toPackage.isEmpty) {
      return Seq.empty
    }

    // left: a.b.c.d
    // right: a.b.c.e

    var srcPkg = fromPackage
    var destPkg = t.path.toPackage
    var matching = 0

    breakable{
      for( i <- srcPkg.indices){
        if (destPkg.size < i || pathDiffers(srcPkg, destPkg, i)) {
          break
        }

        matching += 1
      }
    }

    srcPkg = srcPkg.drop(matching)
    destPkg = destPkg.drop(matching)

    if (srcPkg.isEmpty && destPkg.isEmpty) {
      // On the same level, let's just import the type file
      return Seq(s"./${t.name}")
    }

    var importOffset = ""
    var importFile = ""

    if (srcPkg.nonEmpty && manifest.layout == TypeScriptProjectLayout.YARN) {
      val conv = new TypescriptNamingConvention(manifest)
      importOffset = conv.toScopedId(t.path.toPackage)
      importFile = importOffset
    } else {
      if (srcPkg.nonEmpty) {
        (1 to srcPkg.size).foreach(_ => importOffset += "../")
      } else {
        importOffset = "./"
      }

      importFile = importOffset + destPkg.mkString("/")
    }

    Seq(importFile)
  }

  private def pathDiffers(srcPkg : Package, destPkg: Package, depth: Int) : Boolean = {
    Try(srcPkg(depth) != destPkg(depth)).getOrElse(true)
  }

  protected def fromTypes(types: List[TypeId], fromPkg: Package, extra: List[TypeScriptImport] = List.empty, manifest: TypeScriptBuildManifest): List[TypeScriptImport] = {
    val imports = types.distinct
    if (fromPkg.isEmpty) {
      return List.empty
    }

    imports.flatMap( i => this.withImport(i, fromPkg, manifest).map(wi => (i, wi))).filterNot(_._2.isEmpty).map(m => TypeScriptImport(m._1, m._2)) ++ extra
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
      ts.structure.structure(i).all.flatMap(f => List(f.field.typeId) ++ collectTypes(ts, if(f.defn.variance.nonEmpty) f.defn.variance.last.typeId else f.field.typeId)).filterNot(_ == definition.id) ++
      ts.inheritance.allParents(i.id).filterNot(i.struct.superclasses.interfaces.contains).filterNot(ff => ff == i.id).map(ifc => ts.tools.implId(ifc))
    case d: DTO =>
      d.struct.superclasses.interfaces ++
      ts.structure.structure(d).all.flatMap(f => List(f.field.typeId) ++ collectTypes(ts, if(f.defn.variance.nonEmpty) f.defn.variance.last.typeId else f.field.typeId)).filterNot(_ == definition.id) ++
      ts.inheritance.allParents(d.id).filterNot(d.struct.superclasses.interfaces.contains).map(ifc => ts.tools.implId(ifc))
    case a: Adt =>
      a.alternatives.flatMap(al => List(al.typeId) ++ collectTypes(ts, al.typeId))
  }

  /*
  val uniqueInterfaces = ts.inheritance.parentsInherited(i.id).groupBy(_.name).map(_._2.head)
   */

  protected def fromDefinition(ts: Typespace, definition: TypeDef, fromPkg: Package, extra: List[TypeScriptImport] = List.empty, manifest: TypeScriptBuildManifest): List[TypeScriptImport] = {
    val types = collectTypes(ts, definition)
    fromTypes(types, fromPkg, extra, manifest)
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

  protected def fromService(ts: Typespace, svc: Service, fromPkg: Package, extra: List[TypeScriptImport] = List.empty, manifest: TypeScriptBuildManifest): List[TypeScriptImport] = {
    val types = svc.methods.flatMap {
      case m: RPCMethod => m.signature.input.fields.flatMap(f => collectTypes(ts, f.typeId)) ++ fromRPCMethodOutput(ts, m.signature.output)
    }

    fromTypes(types, fromPkg, extra, manifest)
  }

  protected def fromBuzzer(ts: Typespace, i: Buzzer, fromPkg: Package, extra: List[TypeScriptImport] = List.empty, manifest: TypeScriptBuildManifest): List[TypeScriptImport] = {
    val types = i.events.flatMap {
      case m: RPCMethod => m.signature.input.fields.flatMap(f => collectTypes(ts, f.typeId)) ++ fromRPCMethodOutput(ts, m.signature.output)
    }

    fromTypes(types, fromPkg, extra, manifest)
  }
}
