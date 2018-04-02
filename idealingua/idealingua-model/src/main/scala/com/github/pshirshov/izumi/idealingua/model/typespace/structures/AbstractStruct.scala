package com.github.pshirshov.izumi.idealingua.model.typespace.structures

trait AbstractStruct[F] {
  def unambigious: List[F]

  def ambigious: List[F]

  def all: List[F]

  def inherited: List[F] = all.filterNot(isLocal)

  def local: List[F] = all.filter(isLocal)

  def unambigiousInherited: List[F] = unambigious.filterNot(isLocal)

  def localOrAmbigious: List[F] = ambigious ++ local

  def isScalar: Boolean = size == 1

  def isComposite: Boolean = size > 1

  def isEmpty: Boolean = size == 0

  def nonEmpty: Boolean = !isEmpty

  private def size: Int = all.size

  protected def isLocal(f: F): Boolean

}
