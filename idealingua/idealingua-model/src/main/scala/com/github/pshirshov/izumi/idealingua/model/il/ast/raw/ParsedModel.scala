package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.models.Inclusion

final case class ParsedModel private(definitions: Seq[RawTopLevelDefn], includes: Seq[Inclusion])
