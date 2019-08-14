package com.github.pshirshov.izumi.distage.model

package object monadic {
  type DIEffect2[F[_, _]] = DIEffect[F[Throwable, ?]]
}
