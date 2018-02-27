package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLGeneratedType

trait WireProtocol[InWire, OutWire] {
  def encode(generated: IDLGeneratedType): OutWire

  def decode(input: InWire): IDLGeneratedType
}
