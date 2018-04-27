package com.github.pshirshov.izumi.idealingua

import org.scalatest.WordSpec


class ILTranslatorTest extends WordSpec {

  import IDLTestTools._

  "Intermediate language translator" should {
    "be able to produce scala source code" in {
      assert(compilesScala(getClass.getSimpleName, loadDefs()))
    }
    "be able to produce typescript source code" in {
      assert(compilesTypeScript(getClass.getSimpleName, loadDefs()))
    }
    "be able to produce go source code" in {
      assert(compilesGolang(getClass.getSimpleName, loadDefs()))
    }
    "be able to produce charp-unity source code" in {
//      assert(compilesCSharp(getClass.getSimpleName, loadDefs()))
      compilesCSharp(getClass.getSimpleName, loadDefs())
    }
  }
}