package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.FinalDefinition._
import com.github.pshirshov.izumi.idealingua.model.il._

class ILRenderer(domain: DomainDefinition) {
  def render(): String = {
    val sb = new StringBuffer()
    sb.append(render(domain.id))

    sb.append("\n\n")
    sb.append(domain.types.map(render).map(_.trim).mkString("\n\n"))

    sb.append("\n\n")
    sb.append(domain.services.map(render).map(_.trim).mkString("\n\n"))

    sb.toString
  }

  def render(tpe: FinalDefinition): String = {
    tpe match {
      case d: Adt =>
        s"""adt ${render(d.id)} {
           |${d.alternatives.map(render).mkString("\n").shift(2)}
           |}
         """.stripMargin

      case d: Enumeration =>
        s"""enum ${render(d.id)} {
           |${d.members.map(renderString).mkString("\n").shift(2)}
           |}
         """.stripMargin

      case d: Alias =>
        s"alias ${render(d.id)} = ${render(d.target)}\n"

      case d: Identifier =>
        s"""id ${render(d.id)} {
           |${renderAggregate(d.fields).shift(2)}
           |}
         """.stripMargin

      case d: Interface =>
        val body = Seq(renderComposite(d.interfaces), renderAggregate(d.fields)).filterNot(_.isEmpty).mkString("\n\n")
        s"""mixin ${render(d.id)} {
           |${body.shift(2)}
           |}
         """.stripMargin

      case d: DTO =>
        s"""data ${render(d.id)} {
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
      .map(t => s"+ $t")
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
        s"def ${d.name}(${d.signature.input.map(render).mkString(", ")}): (${d.signature.output.map(render).mkString(", ")})"
    }
  }

  def render(typeId: TypeId): String = {
    typeId match {
      case t: EphemeralId => // TODO: impossible case
        s"${render(t.parent)}#${t.name}"

      case g: Generic =>
        s"${renderTypeName(g.pkg, g.name)}${g.args.map(render).mkString("[", ", ", "]")}"

      case t =>
        renderTypeName(t.pkg, t.name)
    }
  }

  private def renderTypeName(pkg: Package, name: TypeName) = {
    Seq(renderPkg(pkg), name).filterNot(_.isEmpty).mkString("#")
  }

  def renderPkg(value: common.Package): String = {
    minimize(value).mkString(".")
  }

  def render(id: ServiceId): String = {
    renderTypeName(id.pkg, id.name)
  }

  def minimize(value: common.Package): common.Package = {
    val domainPkg = domain.id.toPackage
    if (value == domainPkg) {
      Seq.empty
    } else if (value.nonEmpty && domainPkg.last == value.head) {
      value.tail
    } else {
      value
    }
  }

  def render(domainId: DomainId): String = {
    s"domain ${domainId.toPackage.mkString(".")}"
  }

  def renderString(s: String): String = {
    s // TODO: escape
  }
}
