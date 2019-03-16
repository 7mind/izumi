package com.github.pshirshov.izumi.distage.impl

import java.io.ByteArrayInputStream

import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceUseSimple
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import org.scalatest.WordSpec

class OptionalDependencyTest extends WordSpec {

  "Using DIResource object succeeds event if there's no cats on the classpath" in {
    val resource = DIResource.makeSimple(new ByteArrayInputStream(Array())) { i => println(s"closing $i"); i.close() }

    def x[F[_]: DIEffect] = DIEffect[F].pure(1)
    x[Identity]
    DIEffect.fromCatsEffect[Option, DIResource[?[_], Int]](null, null)

    resource.use {
      i => println(i)
    }

  }

}
