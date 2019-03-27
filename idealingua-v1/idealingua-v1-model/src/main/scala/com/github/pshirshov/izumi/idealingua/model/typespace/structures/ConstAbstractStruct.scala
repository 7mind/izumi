package com.github.pshirshov.izumi.idealingua.model.typespace.structures

trait ConstAbstractStruct[F] extends AbstractStruct[F]{
  override lazy val inherited: List[F] = super.inherited

  override lazy val local: List[F] = super.local

  override lazy val unambigiousInherited: List[F] = super.unambigiousInherited

  override lazy val localOrAmbigious: List[F] = super.localOrAmbigious

  override lazy val isScalar: Boolean = super.isScalar

  override lazy val isComposite: Boolean = super.isComposite

  override lazy val isEmpty: Boolean = super.isEmpty

  override lazy val nonEmpty: Boolean = super.nonEmpty

}
