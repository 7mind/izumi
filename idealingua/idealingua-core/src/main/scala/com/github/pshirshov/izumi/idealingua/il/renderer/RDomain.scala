package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.problems.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.Import
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._

class RDomain(context: IDLRenderingContext) extends Renderable[DomainDefinition] {
  import context._

  override def render(domain: DomainDefinition): String = {
    val sb = new StringBuffer()
    sb.append(meta.withMeta(domain.meta.meta, s"domain ${domain.id.render()}"))

    sb.append("\n\n")
    sb.append(domain.meta.directImports.map(renderImport).mkString("\n"))

    sb.append("\n\n")
    sb.append(domain.meta.directInclusions.map(i => s"""include "${i.include}" """).mkString("\n"))

    sb.append("\n\n")
    sb.append(domain.types.filter(m => isThis(domain, m.meta)).map(_.render()).map(_.trim).mkString("\n\n"))

    sb.append("\n\n")
    sb.append(domain.services.filter(m => isThis(domain, m.meta)).map(_.render()).map(_.trim).mkString("\n\n"))

    sb.append("\n\n")
    sb.append(domain.buzzers.filter(m => isThis(domain, m.meta)).map(_.render()).map(_.trim).mkString("\n\n"))

    sb.append("\n\n")
    sb.append(domain.streams.filter(m => isThis(domain, m.meta)).map(_.render()).map(_.trim).mkString("\n\n"))

    // TODO: render constants

    // TODO: render foreign types

    sb.toString

  }

  private def renderImport(i: Import): String = {

    if (i.identifiers.isEmpty) {
      s"import ${i.id.render()}"
    } else {
      val ids = i.identifiers.map {i =>
        i.as match {
          case Some(value) =>
            s"${i.name} as $value"
          case None =>
            i.name
        }}
      s"import ${i.id.render()}.${ids.mkString("{", ", ", "}")}"
    }
  }

  private def isThis(domain: DomainDefinition, meta: NodeMeta): Boolean = {
    options.expandIncludes || {
      meta.pos match {
        case InputPosition.Defined(_, _, file) =>
          file == domain.meta.origin
        case InputPosition.Undefined =>
          throw new IDLException(s"Unexpected empty meta")
      }
    }
  }
}
