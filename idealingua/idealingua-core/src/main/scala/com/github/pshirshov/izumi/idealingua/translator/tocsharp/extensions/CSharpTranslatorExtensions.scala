package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSTContext
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.output.Module

class CSharpTranslatorExtensions(ctx: CSTContext, extensions: Seq[CSharpTranslatorExtension]) {
  def preModelEmit(ctx: CSTContext, id: TypeDef): String = id match {
    case i: Identifier => extensions.map(ex => ex.preModelEmit(ctx, i)).filterNot(_.isEmpty).mkString("\n")
    case e: Enumeration => extensions.map(ex => ex.preModelEmit(ctx, e)).filterNot(_.isEmpty).mkString("\n")
    case _ => ""
  }

  def postModelEmit(ctx: CSTContext, id: TypeDef): String = id match {
    case i: Identifier => extensions.map(ex => ex.postModelEmit(ctx, i)).filterNot(_.isEmpty).mkString("\n")
    case e: Enumeration => extensions.map(ex => ex.postModelEmit(ctx, e)).filterNot(_.isEmpty).mkString("\n")
    case _ => ""
  }

  def imports(ctx: CSTContext, id: TypeDef): Seq[String] = id match {
    case i: Identifier => extensions.flatMap(ex => ex.imports(ctx, i))
    case e: Enumeration => extensions.flatMap(ex => ex.imports(ctx, e))
    case _ => List.empty
  }

  def postEmitModules(ctx: CSTContext, id: TypeDef): Seq[Module] = id match {
    case i: Identifier => extensions.flatMap(ex => ex.postEmitModules(ctx, i))
    case e: Enumeration => extensions.flatMap(ex => ex.postEmitModules(ctx, e))
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
