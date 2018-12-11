package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawNodeMeta, RawTopLevelDefn}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.Import
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.models.Inclusion
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath

trait CompletelyLoadedDomain {
  def id: DomainId
  def imports: Seq[Import]
  def members: Seq[RawTopLevelDefn]
  def referenced: Map[DomainId, CompletelyLoadedDomain]
  def origin: FSPath
  def directInclusions: Seq[Inclusion]
  def meta: RawNodeMeta
}
