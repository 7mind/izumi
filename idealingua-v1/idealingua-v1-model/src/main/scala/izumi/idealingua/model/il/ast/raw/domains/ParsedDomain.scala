package izumi.idealingua.model.il.ast.raw.domains

import izumi.idealingua.model.il.ast.raw.models.ParsedModel

final case class ParsedDomain(
                               decls: DomainHeader
                               , model: ParsedModel
                             )
