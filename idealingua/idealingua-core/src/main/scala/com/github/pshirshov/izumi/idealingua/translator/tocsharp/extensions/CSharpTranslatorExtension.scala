package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSTContext
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.CogenProduct.EnumProduct
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
  //
  //  def handleIdentifier(ctx: TSTContext, identifier: TypeDef.Identifier, product: IdentifierProduct): IdentifierProduct = {
  //    discard(ctx, identifier)
  //    product
  //  }
}


