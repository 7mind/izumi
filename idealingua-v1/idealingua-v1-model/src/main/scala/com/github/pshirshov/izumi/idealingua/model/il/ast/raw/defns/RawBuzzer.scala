package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.BuzzerId

final case class RawBuzzer(id: BuzzerId, events: List[RawMethod], meta: RawNodeMeta)
