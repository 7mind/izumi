package com.github.pshirshov.izumi.idealingua

import java.nio.file.Paths

import com.github.pshirshov.izumi.fundamentals.platform.jvm.IzJvm
import com.github.pshirshov.izumi.idealingua.il.loader.LocalFilesystemEnumerator
import com.github.pshirshov.izumi.idealingua.il.renderer.IDLRenderer
import com.github.pshirshov.izumi.idealingua.model.loader.UnresolvedDomains
import org.scalatest.WordSpec


class LoaderTest extends WordSpec {

  "IL loader" should {
    "parse all test domains" in {
      val loader = IDLTestTools.makeLoader()
      val defs = IDLTestTools.loadDefs(loader)

      assert(defs.nonEmpty)

      defs.foreach {
        original =>
          val domainId = original.typespace.domain.id
          assert(original.typespace.domain.nonEmpty, s"$domainId parsed into empty definition (location: ${original.path})")
      }

      val files = loader.enumerator.enumerate()

      defs.foreach {
        original =>
          val ts = original.typespace
          val domainId = ts.domain.id
          val rendered = new IDLRenderer(ts.domain).render()

          val updated = files.updated(original.path, rendered)

          // TODO: ModelLoaderImpl
          val domains = loader.parser.parseDomains(updated.filter(_._1.name.endsWith(loader.domainExt)))
          val models = loader.parser.parseModels(updated.filter(_._1.name.endsWith(loader.modelExt)))
          val unresolved = UnresolvedDomains(domains, models)
          val typespaces = loader.resolver.resolve(unresolved).throwIfFailed().successful
          val restoredTypespace = typespaces.find(_.typespace.domain.id == domainId).get.typespace

          // TODO: custom equality check ignoring meta
          assert(restoredTypespace.domain.types.toSet == ts.domain.types.toSet, domainId)
          assert(restoredTypespace.domain.services.toSet == ts.domain.services.toSet, domainId)
          assert(restoredTypespace.domain.buzzers.toSet == ts.domain.buzzers.toSet, domainId)
          assert(restoredTypespace.domain.streams.toSet == ts.domain.streams.toSet, domainId)
      }
    }
  }

  "FS enumerator" should {
    "be able to find files in jars" in {
      val classpath = IzJvm.safeClasspath().split(':')
      val enumerator = new LocalFilesystemEnumerator(Paths.get("/tmp/nonexistent"), classpath.filter(_.contains("fastparse")).map(p => Paths.get(p).toFile), Set(".MF"))
      assert(enumerator.enumerate().size == 1)
    }
  }


}

