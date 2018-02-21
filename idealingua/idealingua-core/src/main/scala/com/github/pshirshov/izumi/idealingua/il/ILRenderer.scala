package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.finaldef.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.finaldef.FinalDefinition._
import com.github.pshirshov.izumi.idealingua.model.finaldef._

class ILRenderer(domain: DomainDefinition) {
  def render(): String = {
    val sb = new StringBuffer()
    sb.append(render(domain.id))

    sb.append("\n\n")
    domain.types.foreach {
      d =>
        sb.append(render(d))
        sb.append("\n")
    }

    sb.append("\n\n")
    domain.services.foreach {
      d =>
        sb.append(render(d))
        sb.append("\n")
    }

    sb.toString
  }

  def render(tpe: FinalDefinition): String = {
    tpe match {
      case d: Enumeration =>
        s"""enum ${render(d.id)} = ${d.members.map(renderString).mkString(" | ")}
           |
         """.stripMargin

      case d: Alias =>
        s"""alias ${render(d.id)} = ${render(d.target)}
           |
         """.stripMargin

      case d: Identifier =>
        s"""id ${render(d.id)} {
           |${renderAggregate(d.fields).shift(2)}
           |}
         """.stripMargin

      case d: Interface =>
        val body = Seq(renderComposite(d.interfaces), renderAggregate(d.fields)).filterNot(_.isEmpty).mkString("\n\n")
        s"""trait ${render(d.id)} {
           |${body.shift(2)}
           |}
         """.stripMargin

      case d: DTO =>
        s"""dto ${render(d.id)} {
           |${renderComposite(d.interfaces).shift(2)}
           |}
         """.stripMargin
    }
  }

  def render(service: Service): String = {
    s"""service ${render(service.id)} {
       |${service.methods.map(render).mkString("\n").shift(2)}
       |}
     """.stripMargin
  }

  def renderComposite(aggregate: Composite): String = {
    aggregate
      .map(render)
      .map(t => s": $t")
      .mkString("\n")
  }

  def renderAggregate(aggregate: Aggregate): String = {
    aggregate
      .map(render)
      .mkString("\n")
  }

  def render(field: Field): String = {
    s"${field.name}: ${render(field.typeId)}"
  }

  def render(tpe: DefMethod): String = {
    tpe match {
      case d: RPCMethod =>
        s"${d.name}(${d.signature.input.map(render).mkString(", ")}): (${d.signature.output.map(render).mkString(", ")})"
    }
  }

  def render(typeId: TypeId): String = {
    typeId match {
      case t: EphemeralId => // TODO: impossible case
        s"${render(t.parent)}#${t.name}"

      case t =>
        Seq(renderPkg(t.pkg), t.name).filterNot(_.isEmpty).mkString("#")
    }
  }

  def renderPkg(value: common.Package): String = {
    minimize(value).mkString(".")
  }

  def render(id: ServiceId): String = {
    Seq(renderPkg(id.pkg), id.name).filterNot(_.isEmpty).mkString("#")
  }

  def minimize(value: common.Package): common.Package = {
    value.map(Option.apply)
      .zipAll(domain.id.toPackage.map(Option.apply), None, None)
      .filter(pair => pair._1 == pair._2).flatMap(_._1)
  }

  def render(domainId: DomainId): String = {
    s"domain ${domainId.toPackage.mkString(".")}"
  }

  def renderString(s: String): String = {
    s // TODO: escape
  }
}
