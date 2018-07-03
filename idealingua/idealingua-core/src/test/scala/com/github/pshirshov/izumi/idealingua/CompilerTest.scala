package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import org.scalatest.WordSpec


class CompilerTest extends WordSpec {

  import IDLTestTools._

  "IDL compiler" should {
    "be able to compile into scala" in {
      assert(compilesScala(getClass.getSimpleName, loadDefs()))
    }
    "be able to compile into typescript" in {
      assume(IzFiles.which("tsc").nonEmpty)
      assert(compilesTypeScript(getClass.getSimpleName, loadDefs()))
    }
    "be able to compile into golang" in {
      assume(IzFiles.which("go").nonEmpty)
      assert(compilesGolang(getClass.getSimpleName, loadDefs()))
    }
    "be able to compile into csharp" in {
      assume(IzFiles.which("csc").nonEmpty)
      assert(compilesCSharp(getClass.getSimpleName, loadDefs()))
    }
  }
}

