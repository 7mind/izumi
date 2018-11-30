package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{CompletelyLoadedDomain, IL}
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath

import scala.collection.mutable

private[loader] class CompletelyLoadedDomainMutable
(
  override val id: DomainId,
  override val members: Seq[IL.Val],
  override val origin: FSPath,
  refContext: mutable.Map[DomainId, CompletelyLoadedDomain],
  requiredRefs: Set[DomainId]
) extends CompletelyLoadedDomain {

  override def referenced: Map[DomainId, CompletelyLoadedDomain] = {
    refContext.filterKeys(requiredRefs.contains).toMap
  }

}
