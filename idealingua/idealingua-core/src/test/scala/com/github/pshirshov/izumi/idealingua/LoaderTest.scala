package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader.{parseDomains, parseModels}
import com.github.pshirshov.izumi.idealingua.il.parser.IDLParser
import com.github.pshirshov.izumi.idealingua.il.renderer.ILRenderer
import fastparse.core.Parsed
import org.scalatest.WordSpec


class LoaderTest extends WordSpec {

  "IL loader" should {
    "parse all test domains" in {
      val loader = IDLTestTools.makeLoader()
      val defs = IDLTestTools.loadDefs(loader)

      assert(defs.nonEmpty)

      val files = loader.enumerate()
      val domains = parseDomains(files)
      val models = parseModels(files)

      defs.foreach {
        d =>
          val domainId = d.domain.id
          assert(d.domain.types.nonEmpty, domainId)

          val rendered = new ILRenderer(d.domain).render()
          val restored = IDLParser.parseDomain(rendered)

          restored match {
            case Parsed.Success(r, _) =>
              val typespaces = loader.resolve(domains.updated(domainId, r), models)
              val restoredTypespace = typespaces.find(_.domain.id == domainId).get
              assert(restoredTypespace.domain.types.toSet == d.domain.types.toSet)
              assert(restoredTypespace.domain.services.toSet == d.domain.services.toSet)

            case f =>
              fail(s"Failed to reparse $domainId: $f\nDOMAIN:\n$rendered")
          }

      }
    }
  }


}

