package com.github.pshirshov.izumi.idealingua

import java.io.File

import com.github.pshirshov.izumi.idealingua.translator.ModelLoader
import org.scalatest.WordSpec


class LoaderTest extends WordSpec {
  import IDLTest._

  "IL loader" should {
    "load & parse domain definition" in {
      val src = new File(getClass.getResource("/defs").toURI).toPath
      val loader = new ModelLoader(src)
      val loaded = loader.load()
      assert(loaded.lengthCompare(2) == 0)
      loaded.foreach {
        d =>
        assert(compiles(d))
      }
    }
  }
}
