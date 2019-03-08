package com.github.pshirshov.izumi.distage.impl

import java.io.ByteArrayInputStream

import com.github.pshirshov.izumi.distage.model.definition.DIResource
import org.scalatest.WordSpec

class OptionalDependencyTest extends WordSpec {

  "Using DIResource object succeeds event if there's no cats on the classpath" in {
    val resource = DIResource.apply(new ByteArrayInputStream(Array())) { i => println(s"closing $i"); i.close() }

    resource.use {
      i => println(i)
    }
  }

}
