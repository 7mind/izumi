package com.github.pshirshov.izumi.distage.impl

import java.io.ByteArrayInputStream
import java.security.Identity

import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.model.monadic.DIMonad
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import org.scalatest.WordSpec

class OptionalDependencyTest extends WordSpec {

  "Using DIResource object succeeds event if there's no cats on the classpath" in {
    val resource = DIResource.apply(new ByteArrayInputStream(Array())) { i => println(s"closing $i"); i.close() }

    def x[F[_]: DIMonad] = DIMonad[F].pure(1)

    resource.use {
      i => println(i)
    }

    assertCompiles("x[Identity]")
  }

}
