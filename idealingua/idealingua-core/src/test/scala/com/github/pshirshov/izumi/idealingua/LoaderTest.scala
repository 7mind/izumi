package com.github.pshirshov.izumi.idealingua

import java.io.File

import com.github.pshirshov.izumi.idealingua.translator.ModelLoader
import org.scalatest.WordSpec


class LoaderTest extends WordSpec {
  "IL loader" should {
    "load & parse domain definition" in {
      val src = new File(getClass.getResource("/defs").toURI).toPath
      val loader = new ModelLoader(src)
      assert(loader.load().lengthCompare(2) == 0)
    }
  }
}
