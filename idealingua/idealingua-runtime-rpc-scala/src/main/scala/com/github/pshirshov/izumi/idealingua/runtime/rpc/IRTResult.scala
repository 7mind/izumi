package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds

trait IRTResult {
    type Or[E, V]
    type Just[V]

    def choice[E, V](v: => Either[E, V]): Or[E, V]
    def just[V](v: => V): Just[V]
}
