package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.models.Inclusion
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath

final case class DomainMeshLoaded(
                                   id: DomainId,
                                   origin: FSPath,
                                   directInclusions: Seq[Inclusion],
                                   originalImports: Seq[Import],
                                   meta: RawNodeMeta,
                                   types: Seq[RawTypeDef],
                                   streams: Seq[RawStreams],
                                   consts: Seq[RawConstBlock],
                                   imports: Seq[SingleImport],
                                   referenced: Map[DomainId, DomainMeshLoaded],
                                 )
