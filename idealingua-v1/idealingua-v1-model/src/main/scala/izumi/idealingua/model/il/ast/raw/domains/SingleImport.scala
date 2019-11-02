package izumi.idealingua.model.il.ast.raw.domains

import izumi.idealingua.model.common.DomainId

final case class SingleImport(domain: DomainId, imported: ImportedId)
