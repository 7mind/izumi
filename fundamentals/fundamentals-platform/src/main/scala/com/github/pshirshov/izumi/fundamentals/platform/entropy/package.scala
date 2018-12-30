package com.github.pshirshov.izumi.fundamentals.platform

package object entropy {
  type Entropy2[F[_, _]] = Entropy[F[Nothing, ?]]
}
