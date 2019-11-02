package izumi.idealingua.model.il.ast.raw.models

import izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn

final case class ParsedModel private(definitions: Seq[RawTopLevelDefn], includes: Seq[Inclusion])
