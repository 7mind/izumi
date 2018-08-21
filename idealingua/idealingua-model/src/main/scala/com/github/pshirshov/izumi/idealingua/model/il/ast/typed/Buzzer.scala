package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.BuzzerId

final case class Buzzer(id: BuzzerId, events: List[DefMethod], doc: NodeMeta)


