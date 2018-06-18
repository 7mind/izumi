package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.{CSTContext, CSharpImports}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.CogenProduct.{EnumProduct, IdentifierProduct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

trait CSharpTranslatorExtension extends TranslatorExtension {
    import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

    // Enumeration
    def preModelEmit(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): String = {
      discard(ctx, id)
      ""
    }

    def postModelEmit(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): String = {
      discard(ctx, id)
      ""
    }

    def imports(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
      discard(ctx, id)
      List.empty
    }

    def postEmitModules(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
      discard(ctx, id)
      List.empty
    }

    // Identifier
    def preModelEmit(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): String = {
      discard(ctx, id)
      ""
    }

    def postModelEmit(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): String = {
      discard(ctx, id)
      ""
    }

    def imports(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
      discard(ctx, id)
      List.empty
    }

    def postEmitModules(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
      discard(ctx, id)
      List.empty
    }

    // Interface
    def preModelEmit(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): String = {
      discard(ctx, id)
      ""
    }

    def postModelEmit(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): String = {
      discard(ctx, id)
      ""
    }

    def imports(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
      discard(ctx, id)
      List.empty
    }

    def postEmitModules(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
      discard(ctx, id)
      List.empty
    }

    // DTO
    def preModelEmit(ctx: CSTContext, id: DTO)(implicit im: CSharpImports, ts: Typespace): String = {
      discard(ctx, id)
      ""
    }

    def postModelEmit(ctx: CSTContext, id: DTO)(implicit im: CSharpImports, ts: Typespace): String = {
      discard(ctx, id)
      ""
    }

    def imports(ctx: CSTContext, id: DTO)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
      discard(ctx, id)
      List.empty
    }

    def postEmitModules(ctx: CSTContext, id: DTO)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
      discard(ctx, id)
      List.empty
    }

    // ADT
    def preModelEmit(ctx: CSTContext, id: Adt)(implicit im: CSharpImports, ts: Typespace): String = {
      discard(ctx, id)
      ""
    }

    def postModelEmit(ctx: CSTContext, id: Adt)(implicit im: CSharpImports, ts: Typespace): String = {
      discard(ctx, id)
      ""
    }

    def imports(ctx: CSTContext, id: Adt)(implicit im: CSharpImports, ts: Typespace): Seq[String] = {
      discard(ctx, id)
      List.empty
    }

    def postEmitModules(ctx: CSTContext, id: Adt)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
      discard(ctx, id)
      List.empty
    }
}


