package com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.{DTO, Interface}
import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TSTContext
import com.github.pshirshov.izumi.idealingua.translator.totypescript.products.CogenProduct._

trait TypeScriptTranslatorExtension extends TranslatorExtension {
  import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

  def handleInterface(ctx: TSTContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    discard(ctx, interface, manifest)
    product
  }

  def handleDTO(ctx: TSTContext, dto: DTO, product: CompositeProduct): CompositeProduct = {
    discard(ctx, dto, manifest)
    product
  }

  def handleEnum(ctx: TSTContext, enum: TypeDef.Enumeration, product: EnumProduct): EnumProduct = {
    discard(ctx, enum, manifest)
    product
  }

  def handleIdentifier(ctx: TSTContext, identifier: TypeDef.Identifier, product: IdentifierProduct): IdentifierProduct = {
    discard(ctx, identifier, manifest)
    product
  }

  def handleAdt(ctx: TSTContext, adt: TypeDef.Adt, product: AdtProduct): AdtProduct = {
    discard(ctx, adt, manifest)
    product
  }
}


