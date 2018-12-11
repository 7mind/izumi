package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawNodeMeta, RawTopLevelDefn}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.Import
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.models.Inclusion
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath

import scala.collection.mutable

private[loader] class CompletelyLoadedDomainMutable
(
  override val id: DomainId,
  override val members: Seq[RawTopLevelDefn],
  override val origin: FSPath,
  override val directInclusions: Seq[Inclusion],
  override val imports: Seq[Import],
  override val meta: RawNodeMeta,
  refContext: mutable.Map[DomainId, CompletelyLoadedDomain],
  requiredRefs: Set[DomainId],
) extends CompletelyLoadedDomain {

  override def referenced: Map[DomainId, CompletelyLoadedDomain] = {
    refContext.filterKeys(requiredRefs.contains).toMap
  }

}
