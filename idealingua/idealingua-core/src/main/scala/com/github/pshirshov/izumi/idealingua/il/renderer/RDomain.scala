package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._

class RDomain()(
  implicit ev1: Renderable[TypeDef]
  , ev2: Renderable[Service]
  , ev3: Renderable[Emitter]
  , ev4: Renderable[Streams]
  , ev5: Renderable[DomainId]
) extends Renderable[DomainDefinition] {
  override def render(domain: DomainDefinition): String = {
    val sb = new StringBuffer()
    sb.append(s"domain ${domain.id.render()}")

    sb.append("\n\n")
    sb.append(domain.referenced.keys.map(renderImport).mkString("\n"))

    sb.append("\n\n")
    sb.append(domain.types.map(_.render()).map(_.trim).mkString("\n\n"))

    sb.append("\n\n")
    sb.append(domain.services.map(_.render()).map(_.trim).mkString("\n\n"))

    sb.append("\n\n")
    sb.append(domain.emitters.map(_.render()).map(_.trim).mkString("\n\n"))

    sb.append("\n\n")
    sb.append(domain.streams.map(_.render()).map(_.trim).mkString("\n\n"))

    sb.toString

  }

  private def renderImport(domain: DomainId): String = {
    s"import ${domain.render()}"
  }
}
