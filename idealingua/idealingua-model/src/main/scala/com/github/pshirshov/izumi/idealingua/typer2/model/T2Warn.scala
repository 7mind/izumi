package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawRef
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{BasicField, NodeMeta}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.IzTypeArgName

sealed trait T2Warn

object T2Warn {
  sealed trait WithMeta extends T2Warn {
    def meta: NodeMeta
  }
  final case class MissingFieldsToRemove(tpe: IzTypeId, removals: Set[BasicField], meta: NodeMeta) extends WithMeta
  final case class MissingParentsToRemove(tpe: IzTypeId, removals: Set[RawRef], meta: NodeMeta) extends WithMeta
  final case class MissingBranchesToRemove(tpe: IzTypeId, removals: Set[String], meta: NodeMeta) extends WithMeta
  final case class TemplateInstanceNameWillBeGenerated(generic: IzTypeId, generatedName: String, meta: NodeMeta) extends WithMeta
  final case class UnusedForeignTypeParameters(id: IzTypeId, undefinedParameters: Set[IzTypeArgName], meta: NodeMeta) extends WithMeta
  final case class FailedConversion(failed: Conversion) extends T2Warn

}
