package com.github.pshirshov.izumi.idealingua

import java.io.File

import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.model.il.ast.DomainDefinition
import org.scalatest.WordSpec


class LoadThenCompileTest extends WordSpec {

  import IDLTestTools._

  "IL loader" should {
    "load & parse domain definition" in {
      assert(compiles(getClass.getSimpleName, loadDefs()))
    }
  }


}

