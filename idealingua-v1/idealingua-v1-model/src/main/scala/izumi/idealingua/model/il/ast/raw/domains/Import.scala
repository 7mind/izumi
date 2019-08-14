package izumi.idealingua.model.il.ast.raw.domains

import izumi.idealingua.model.common.DomainId

final case class Import(id: DomainId, identifiers: Set[ImportedId])
