package com.github.pshirshov.izumi.idealingua.model.finaldef

import com.github.pshirshov.izumi.idealingua.model.TypeId

case class Service(id: TypeId, methods: Seq[DefMethod])
