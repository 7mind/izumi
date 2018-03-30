package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.translator.IDLLanguage
import org.scalatest.WordSpec


class ILTranslatorTest extends WordSpec {

  import IDLTestTools._

  "Intermediate language translator" should {
    "be able to produce scala source code" in {
      assert(compiles(getClass.getSimpleName, Seq(Model01.domain, Model02.domain), IDLLanguage.Scala))
    }

    "be able to produce golang source code" in {
      assert(compiles(getClass.getSimpleName, Seq(Model01.domain, Model02.domain), IDLLanguage.Go))
    }
  }

}




