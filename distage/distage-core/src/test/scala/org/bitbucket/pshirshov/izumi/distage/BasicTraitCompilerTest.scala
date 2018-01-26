package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.provisioning.traitcompiler.TraitConstructorMacro
import org.scalatest.WordSpec

class BasicTraitCompilerTest extends WordSpec {

  trait Aaa {
    def a: Int
    def b: Boolean
  }

  "Trait compiler (whitebox tests)" should {
    "construct a basic trait" in {
     val ctor = TraitConstructorMacro.mkTraitConstructor[Aaa]

     val value = ctor(5, false)

     assert(value.isInstanceOf[Aaa])
     assert(value.a == 5)
     assert(value.b == false)

    }
  }

}
