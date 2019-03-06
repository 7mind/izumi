package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawNodeMeta, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.NodeMeta
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzNamespace
import com.github.pshirshov.izumi.idealingua.typer2.results._


trait Interpreter2 {
  def makeIdentifier(i: RawTypeDef.Identifier, subpath: Seq[IzNamespace]): TSingleT[IzType.Identifier]

  def makeInterface(i: RawTypeDef.Interface, subpath: Seq[IzNamespace]): TSingle

  def makeDto(i: RawTypeDef.DTO, subpath: Seq[IzNamespace]): TSingle

  def makeAlias(a: RawTypeDef.Alias, subpath: Seq[IzNamespace]): TSingleT[IzType.IzAlias]

  def makeEnum(e: RawTypeDef.Enumeration, subpath: Seq[IzNamespace]): TSingle

  def meta(meta: RawNodeMeta): NodeMeta

}
