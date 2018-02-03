package com.github.pshirshov.izumi.idealingua.model.finaldef

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

case class Service(id: TypeId, methods: Seq[DefMethod])
