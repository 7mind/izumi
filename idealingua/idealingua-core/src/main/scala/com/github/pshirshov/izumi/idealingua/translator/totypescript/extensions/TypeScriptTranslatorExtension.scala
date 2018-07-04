package com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions

import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TSTContext
import com.github.pshirshov.izumi.idealingua.translator.totypescript.products.CogenProduct._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.{DTO, Interface}

trait TypeScriptTranslatorExtension extends TranslatorExtension {
  import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

  def handleInterface(ctx: TSTContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    discard(ctx, interface)
    product
  }

  def handleDTO(ctx: TSTContext, dto: DTO, product: CompositeProduct): CompositeProduct = {
    discard(ctx, dto)
    product
  }

  def handleEnum(ctx: TSTContext, enum: TypeDef.Enumeration, product: EnumProduct): EnumProduct = {
    discard(ctx, enum)
    product
  }

  def handleIdentifier(ctx: TSTContext, identifier: TypeDef.Identifier, product: IdentifierProduct): IdentifierProduct = {
    discard(ctx, identifier)
    product
  }

  def handleAdt(ctx: TSTContext, adt: TypeDef.Adt, product: AdtProduct): AdtProduct = {
    discard(ctx, adt)
    product
  }
}


