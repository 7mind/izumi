package com.github.pshirshov.izumi.idealingua

import java.io.File

import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import org.scalatest.WordSpec


class LoadThenCompileTest extends WordSpec {
  import ILTranslatorTest._

  "IL loader" should {
    "load & parse domain definition" in {
      val src = new File(getClass.getResource("/defs").toURI).toPath
      val loader = new LocalModelLoader(src, Seq.empty)
      val loaded = loader.load()
      assert(loaded.size == 4)
      assert(compiles(getClass.getSimpleName, loaded))
    }
  }
}
