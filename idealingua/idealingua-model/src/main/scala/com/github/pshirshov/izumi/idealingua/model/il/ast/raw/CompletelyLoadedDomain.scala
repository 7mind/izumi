package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath

trait CompletelyLoadedDomain {
  def id: DomainId
  def members: Seq[IL.Val]
  def referenced: Map[DomainId, CompletelyLoadedDomain]
  def origin: FSPath
  def directInclusions: Seq[String]
}
