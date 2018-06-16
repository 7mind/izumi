package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSTContext
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.CogenProduct.{EnumProduct, IdentifierProduct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.output.Module

trait CSharpTranslatorExtension extends TranslatorExtension {
    import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

    def preModelEmit(ctx: CSTContext, id: Enumeration): String = {
      discard(ctx, id)
      ""
    }

    def postModelEmit(ctx: CSTContext, id: Enumeration): String = {
      discard(ctx, id)
      ""
    }

    def imports(ctx: CSTContext, id: Enumeration): Seq[String] = {
      discard(ctx, id)
      List.empty
    }

    def postEmitModules(ctx: CSTContext, id: Enumeration): Seq[Module] = {
      discard(ctx, id)
      List.empty
    }

    def preModelEmit(ctx: CSTContext, id: Identifier): String = {
      discard(ctx, id)
      ""
    }

    def postModelEmit(ctx: CSTContext, id: Identifier): String = {
      discard(ctx, id)
      ""
    }

    def imports(ctx: CSTContext, id: Identifier): Seq[String] = {
      discard(ctx, id)
      List.empty
    }

    def postEmitModules(ctx: CSTContext, id: Identifier): Seq[Module] = {
      discard(ctx, id)
      List.empty
    }
}


