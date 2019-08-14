package com.github.pshirshov.izumi.distage.impl

import java.io.ByteArrayInputStream

import com.github.pshirshov.izumi.distage.model.definition.DIResource
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, FromCats}
import com.github.pshirshov.izumi.functional.bio.{BIO, BIOAsync}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import org.scalatest.{GivenWhenThen, WordSpec}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

class OptionalDependencyTest extends WordSpec with GivenWhenThen {

  "Using DIResource & DIEffect objects succeeds event if there's no cats or zio on the classpath" in {

    Then("DIEffect methods can be called")
    def x[F[_]: DIEffect] = DIEffect[F].pure(1)

    And("DIEffect in DIEffect object resolve")
    assert(x[Identity] == 1)

    try DIEffect.fromBIO(null) catch { case _: NullPointerException => }
    try BIO.apply[Either, Unit](())(null) catch { case _: NullPointerException => }

    And("Methods that mention cats/ZIO types directly cannot be referred to in code")
//    assertDoesNotCompile("DIEffect.fromBIO(BIO.BIOZio)")
//    assertDoesNotCompile("DIResource.fromCats(null)")
//    assertDoesNotCompile("DIResource.providerFromCats(null)(null)")
    BIOAsync.apply[Either](null)

    And("Can search for BIO/BIOAsync")
    assertTypeError("implicitly[BIOAsync[Either]]")
    assertTypeError("implicitly[BIO[Either]]")

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
        assert(i.read() == -1)
    }
    assert(!open)

//    Then("DIResource.toCats doesn't work")
//    assertDoesNotCompile("resource.toCats")
  }

}
