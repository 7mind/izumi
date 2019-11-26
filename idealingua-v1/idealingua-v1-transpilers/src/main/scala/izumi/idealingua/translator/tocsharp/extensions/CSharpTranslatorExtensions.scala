package izumi.idealingua.translator.tocsharp.extensions

import izumi.idealingua.model.common.{Builtin, TypeId}
import izumi.idealingua.model.il.ast.typed.DefMethod.Output.Alternative
import izumi.idealingua.model.il.ast.typed.TypeDef
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.output.Module
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.tocsharp.types.CSharpClass
import izumi.idealingua.translator.tocsharp.{CSTContext, CSharpImports}

class CSharpTranslatorExtensions(ctx: CSTContext, extensions: Seq[CSharpTranslatorExtension]) {
  def preModelEmit(ctx: CSTContext, id: TypeDef)(implicit im: CSharpImports, ts: Typespace): String = id match {
    case i: Identifier => extensions.map(ex => ex.preModelEmit(ctx, i)).filterNot(_.isEmpty).mkString("\n")
    case e: Enumeration => extensions.map(ex => ex.preModelEmit(ctx, e)).filterNot(_.isEmpty).mkString("\n")
    case d: DTO => extensions.map(ex => ex.preModelEmit(ctx, d)).filterNot(_.isEmpty).mkString("\n")
    case i: Interface => extensions.map(ex => ex.preModelEmit(ctx, i)).filterNot(_.isEmpty).mkString("\n")
    case a: Adt => extensions.map(ex => ex.preModelEmit(ctx, a)).filterNot(_.isEmpty).mkString("\n")
    case a: Alias => preModelEmit(ctx, ts.apply(ts.dealias(a.id)))
    case _ => ""
  }

  // This is complimentary to DTO for structs in services responses
  def preModelEmit(ctx: CSTContext, name: String, struct: CSharpClass)(implicit im: CSharpImports, ts: Typespace): String = {
    extensions.map(ex => ex.preModelEmit(ctx, name, struct)).filterNot(_.isEmpty).mkString("\n")
  }

  // This is complimentary to Alternative
  def preModelEmit(ctx: CSTContext, name: String, alternative: Alternative)(implicit im: CSharpImports, ts: Typespace): String = {
    extensions.map(ex => ex.preModelEmit(ctx, name, alternative)).filterNot(_.isEmpty).mkString("\n")
  }

  def postModelEmit(ctx: CSTContext, id: TypeDef)(implicit im: CSharpImports, ts: Typespace): String = id match {
    case i: Identifier => extensions.map(ex => ex.postModelEmit(ctx, i)).filterNot(_.isEmpty).mkString("\n")
    case e: Enumeration => extensions.map(ex => ex.postModelEmit(ctx, e)).filterNot(_.isEmpty).mkString("\n")
    case d: DTO => extensions.map(ex => ex.postModelEmit(ctx, d)).filterNot(_.isEmpty).mkString("\n")
    case i: Interface => extensions.map(ex => ex.postModelEmit(ctx, i)).filterNot(_.isEmpty).mkString("\n")
    case a: Adt => extensions.map(ex => ex.postModelEmit(ctx, a)).filterNot(_.isEmpty).mkString("\n")
    case a: Alias => postModelEmit(ctx, ts.apply(ts.dealias(a.id)))
    case _ => ""
  }

  // This is complimentary to DTO for structs in services responses
  def postModelEmit(ctx: CSTContext, name: String, struct: CSharpClass)(implicit im: CSharpImports, ts: Typespace): String = {
    extensions.map(ex => ex.postModelEmit(ctx, name, struct)).filterNot(_.isEmpty).mkString("\n")
  }

  // This is complimentary to Alternative services responses
  def postModelEmit(ctx: CSTContext, name: String, alternative: Alternative, leftType: TypeId, rightType: TypeId)(implicit im: CSharpImports, ts: Typespace): String = {
    extensions.map(ex => ex.postModelEmit(ctx, name, alternative, leftType, rightType)).filterNot(_.isEmpty).mkString("\n")
  }

  def imports(ctx: CSTContext, id: TypeDef)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
//    println(("?", id))
    id match {
      case i: Identifier => extensions.flatMap(ex => ex.imports(ctx, i))
      case e: Enumeration => extensions.flatMap(ex => ex.imports(ctx, e))
      case d: DTO => extensions.flatMap(ex => ex.imports(ctx, d))
      case i: Interface => extensions.flatMap(ex => ex.imports(ctx, i))
      case a: Adt => extensions.flatMap(ex => ex.imports(ctx, a))
      case a: Alias => ts.dealias(a.id) match {
        case _: Builtin => List.empty
        case v => imports(ctx, ts.apply(v))
      }
      case _ => List.empty
    }
  }

  def postEmitModules(ctx: CSTContext, id: TypeDef)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = id match {
    case i: Identifier => extensions.flatMap(ex => ex.postEmitModules(ctx, i))
    case e: Enumeration => extensions.flatMap(ex => ex.postEmitModules(ctx, e))
    case d: DTO => extensions.flatMap(ex => ex.postEmitModules(ctx, d))
    case i: Interface => extensions.flatMap(ex => ex.postEmitModules(ctx, i))
    case a: Adt => extensions.flatMap(ex => ex.postEmitModules(ctx, a))
    case _ => List.empty
  }

  def postModuleEmit[S, P]
  (
    source: S
    , entity: P
    , entityTransformer: CSharpTranslatorExtension => (CSTContext, S, P) => P
  ): P = {
    extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(ctx, source, acc)
    }
  }
}
