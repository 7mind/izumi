package izumi.idealingua.model.il.ast.raw.domains

import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.il.ast.raw.defns.RawNodeMeta

final case class DomainHeader(id: DomainId, imports: Seq[Import], meta: RawNodeMeta)
