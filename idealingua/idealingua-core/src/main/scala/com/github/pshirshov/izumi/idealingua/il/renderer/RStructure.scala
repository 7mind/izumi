package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Field, Structure, Structures, Tuple}

class RStructure()(implicit ev: Renderable[TypeId], ev1: Renderable[Field]) extends Renderable[Structure] {
  override def render(structure: Structure): String = {
    Seq(
      renderComposite(structure.superclasses.interfaces, "& ")
      , renderComposite(structure.superclasses.concepts, "+ ")
      , renderComposite(structure.superclasses.removedConcepts, "- ")
      , renderAggregate(structure.fields, "")
      , renderAggregate(structure.removedFields, "- ")
    )
      .filterNot(_.isEmpty)
      .mkString("\n")
  }

  private def renderComposite(aggregate: Structures, prefix: String): String = {
    aggregate
      .map(_.render())
      .map(t => s"$prefix$t")
      .mkString("\n")
  }

  private def renderAggregate(aggregate: Tuple, prefix: String): String = {
    aggregate
      .map(_.render())
      .map(t => s"$prefix$t")
      .mkString("\n")
  }


}
