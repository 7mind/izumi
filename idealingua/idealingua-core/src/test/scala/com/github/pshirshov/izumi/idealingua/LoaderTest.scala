package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.il.parser.ILParser
import com.github.pshirshov.izumi.idealingua.il.parser.model.ParsedDomain
import com.github.pshirshov.izumi.idealingua.il.renderer.ILRenderer
import fastparse.core.Parsed
import org.scalatest.WordSpec



class LoaderTest extends WordSpec {

  "IL loader" should {
    "parse all test domains" in {
      val defs = IDLTestTools.loadDefs()

      assert(defs.nonEmpty)

      defs.foreach {
        d =>
          assert(d.domain.types.nonEmpty, d.domain.id)

          val rendered = new ILRenderer(d.domain).render()
          val restored =  new ILParser().fullDomainDef.parse(rendered)

          assert(restored.isInstanceOf[Parsed.Success[ParsedDomain, Char, String]], d.domain.id)
      }
    }
  }


}

