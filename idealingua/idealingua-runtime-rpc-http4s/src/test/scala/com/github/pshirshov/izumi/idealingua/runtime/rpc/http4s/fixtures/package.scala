package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import scalaz.zio

package object fixtures {
  type BiIO[+E, +V] = zio.IO[E, V]
  type CIO[+T] = cats.effect.IO[T]
}
