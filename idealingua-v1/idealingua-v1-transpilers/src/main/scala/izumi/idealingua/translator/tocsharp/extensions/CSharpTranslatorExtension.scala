package izumi.idealingua.translator.tocsharp.extensions

import izumi.idealingua.model.common.TypeId
import izumi.idealingua.model.il.ast.typed.DefMethod.Output.Alternative
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.output.Module
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.TranslatorExtension
import izumi.idealingua.translator.tocsharp.types.CSharpClass
import izumi.idealingua.translator.tocsharp.{CSTContext, CSharpImports}

trait CSharpTranslatorExtension extends TranslatorExtension {
  import izumi.fundamentals.platform.language.Quirks._

  // Enumeration
  def preModelEmit(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts)
    ""
  }

  def postModelEmit(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts)
    ""
  }

  def imports(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
    discard(ctx, id, im, ts)
    List.empty
  }

  def postEmitModules(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
    discard(ctx, id, im, ts)
    List.empty
  }

  // Identifier
  def preModelEmit(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts)
    ""
  }

  def postModelEmit(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts)
    ""
  }

  def imports(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
    discard(ctx, id, im, ts)
    List.empty
  }

  def postEmitModules(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
    discard(ctx, id, im, ts)
    List.empty
  }

  // Interface
  def preModelEmit(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts)
    ""
  }

  def postModelEmit(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts)
    ""
  }

  def imports(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
    discard(ctx, id, im, ts)
    List.empty
  }

  def postEmitModules(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
    discard(ctx, id, im, ts)
    List.empty
  }

  // DTO
  def preModelEmit(ctx: CSTContext, id: DTO)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts)
    ""
  }

  def preModelEmit(ctx: CSTContext, name: String, struct: CSharpClass)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, name, struct, im, ts)
    ""
  }

  def postModelEmit(ctx: CSTContext, id: DTO)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts)
    ""
  }

  def postModelEmit(ctx: CSTContext, name: String, struct: CSharpClass)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, name, struct, im, ts)
    ""
  }

  def imports(ctx: CSTContext, id: DTO)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
    discard(ctx, id, im, ts)
    List.empty
  }

  def postEmitModules(ctx: CSTContext, id: DTO)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
    discard(ctx, id, im, ts)
    List.empty
  }

  // ADT
  def preModelEmit(ctx: CSTContext, id: Adt)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts)
    ""
  }

  def postModelEmit(ctx: CSTContext, id: Adt)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts)
    ""
  }

  def imports(ctx: CSTContext, id: Adt)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
    discard(ctx, id, im, ts)
    List.empty
  }

  def postEmitModules(ctx: CSTContext, id: Adt)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
    discard(ctx, id, im, ts)
    List.empty
  }

  // Alternative
  def preModelEmit(ctx: CSTContext, name: String, alternative: Alternative)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts, name, alternative)
    ""
  }

  def postModelEmit(ctx: CSTContext, name: String, alternative: Alternative, leftType: TypeId, rightType: TypeId)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx, id, im, ts, name, alternative, leftType, rightType)
    ""
  }
}
