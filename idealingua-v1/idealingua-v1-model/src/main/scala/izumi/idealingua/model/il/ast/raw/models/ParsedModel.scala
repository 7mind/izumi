package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.models

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn

final case class ParsedModel private(definitions: Seq[RawTopLevelDefn], includes: Seq[Inclusion])
