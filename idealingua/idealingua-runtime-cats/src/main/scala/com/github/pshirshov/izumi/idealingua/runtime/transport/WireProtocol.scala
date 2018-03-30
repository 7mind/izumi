package com.github.pshirshov.izumi.idealingua.runtime.transport

import com.github.pshirshov.izumi.idealingua.runtime.model.IDLInput


trait WireProtocol[InWire, Request <: IDLInput, Response, OutWire] {
  def decode(input: InWire): Request

  def encode(generated: Response): OutWire
}
