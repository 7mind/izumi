package com.github.pshirshov.izumi.idealingua.model.runtime.transport

import com.github.pshirshov.izumi.idealingua.model.runtime.model.IDLGeneratedType

trait WireProtocol[OnWire] {
  def encode(generated: IDLGeneratedType): OnWire

  def decode(input: OnWire): IDLGeneratedType
}
