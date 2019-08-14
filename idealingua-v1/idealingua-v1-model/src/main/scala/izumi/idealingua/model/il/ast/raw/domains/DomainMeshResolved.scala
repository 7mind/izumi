package izumi.idealingua.model.il.ast.raw.domains

import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.il.ast.raw.defns.{RawNodeMeta, RawTopLevelDefn}
import izumi.idealingua.model.il.ast.raw.models.Inclusion
import izumi.idealingua.model.loader.FSPath

trait DomainMeshResolved {
  def id: DomainId
  def imports: Seq[Import]
  def members: Seq[RawTopLevelDefn]
  def referenced: Map[DomainId, DomainMeshResolved]
  def origin: FSPath
  def directInclusions: Seq[Inclusion]
  def meta: RawNodeMeta
}
