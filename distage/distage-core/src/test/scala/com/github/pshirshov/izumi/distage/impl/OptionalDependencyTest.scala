package com.github.pshirshov.izumi.distage.impl

import java.io.ByteArrayInputStream

import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, FromCats}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import org.scalatest.{GivenWhenThen, WordSpec}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

class OptionalDependencyTest extends WordSpec with GivenWhenThen {

  "Using DIResource & DIEffect objects succeeds event if there's no cats or zio on the classpath" in {

    Then("DIEffect methods can be called")
    def x[F[_]: DIEffect] = DIEffect[F].pure(1)

    And("DIEffect in DIEffect object resolve")
    assert(x[Identity] == 1)

    And("Methods that mention cats types directly cannot be referred to in code")
    assertDoesNotCompile("DIEffect.fromBIO(BIO.BIOZio)")
//    assertDoesNotCompile("DIResource.fromCats(null)")
//    assertDoesNotCompile("DIResource.providerFromCats(null)(null)")

    And("`No More Orphans` type provider is inacessible")
    FromCats.discard()
    assertTypeError(
      """
         def y[R[_[_]]: FromCats._Sync]() = ()
         y()
      """)

    And("Methods that use `No More Orphans` trick can be called with nulls, but will error")
    intercept[NoClassDefFoundError] {
      DIEffect.fromCatsEffect[Option, DIResource[?[_], Int]](null, null)
    }

    And("Methods that mention cats types only in generics can be called with nulls, but will error")
    intercept[ScalaReflectionException] {
      DIResource.providerFromCatsProvider[Identity, Int](null)
    }

    Then("DIResource.use syntax works")
    var open = false
    val resource = DIResource.makeSimple {
      open = true
      new ByteArrayInputStream(Array())
    } { i => open = false; i.close() }

    resource.use {
      i =>
        assert(open)
        assert(i.readAllBytes().isEmpty)
    }
    assert(!open)

    Then("DIResource.toCats doesn't work")
    assertDoesNotCompile("resource.toCats")
  }

}
