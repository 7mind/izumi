package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSTContext
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.CogenProduct.{EnumProduct, IdentifierProduct}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._

trait CSharpTranslatorExtension extends TranslatorExtension {
  import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

  //  def handleInterface(ctx: TSTContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
  //    discard(ctx, interface)
  //    product
  //  }
  //


    def handleEnum(ctx: CSTContext, enum: Enumeration, product: EnumProduct): EnumProduct = {
      discard(ctx, enum)
      product
    }

    def handleIdentifier(ctx: CSTContext, id: Identifier, product: IdentifierProduct): IdentifierProduct = {
      discard(ctx, id)
      product
    }

    def preModelEmit(ctx: CSTContext, id: Enumeration): String = {
      discard(ctx, id)
      ""
    }

    def postModelEmit(ctx: CSTContext, id: Enumeration): String = {
      discard(ctx, id)
      ""
    }

    def imports(ctx: CSTContext, id: Enumeration): List[String] = {
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

    def imports(ctx: CSTContext, id: Identifier): List[String] = {
      discard(ctx, id)
      List.empty
    }
}


