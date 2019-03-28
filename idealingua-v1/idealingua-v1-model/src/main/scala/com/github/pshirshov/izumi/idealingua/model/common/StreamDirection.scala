package com.github.pshirshov.izumi.idealingua.model.common

sealed trait StreamDirection {

}

object StreamDirection {
  final case object ToServer extends StreamDirection
  final case object ToClient extends StreamDirection
}
