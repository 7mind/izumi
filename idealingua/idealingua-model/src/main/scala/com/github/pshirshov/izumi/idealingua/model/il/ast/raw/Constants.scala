package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ConstId

trait RawConst

final case class Constants(id: ConstId, events: List[RawConst])
