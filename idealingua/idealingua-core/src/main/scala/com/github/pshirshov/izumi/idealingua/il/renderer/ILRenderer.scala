package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst.Service.DefMethod._
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il.ast.{DomainDefinition, DomainId, ILAst}

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

  def render(tpe: ILAst): String = {
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
           |${renderPrimitiveAggregate(d.fields).shift(2)}
           |}
         """.stripMargin

      case d: Interface =>
        val body = renderStruct(d.struct)

        s"""mixin ${render(d.id)} {
           |${body.shift(2)}
           |}
         """.stripMargin

      case d: DTO =>
        val body = renderStruct(d.struct)

        s"""data ${render(d.id)} {
           |${body.shift(2)}
           |}
         """.stripMargin
    }
  }

  def renderStruct(struct: Structure): String = {
    Seq(
      renderComposite(struct.superclasses.interfaces, "+ ")
      , renderComposite(struct.superclasses.concepts, "* ")
      , renderComposite(struct.superclasses.removedConcepts, "- ")
      , renderAggregate(struct.fields, "")
      , renderAggregate(struct.removedFields, "- ")
    )
      .filterNot(_.isEmpty)
      .mkString("\n")
  }

  def render(service: Service): String = {
    s"""service ${render(service.id)} {
       |${service.methods.map(render).mkString("\n").shift(2)}
       |}
     """.stripMargin
  }

  def renderComposite(aggregate: Composite, prefix: String): String = {
    aggregate
      .map(render)
      .map(t => s"$prefix$t")
      .mkString("\n")
  }

  def renderAggregate(aggregate: Tuple, prefix: String): String = {
    aggregate
      .map(render)
      .map(t => s"$prefix$t")
      .mkString("\n")
  }

  def renderPrimitiveAggregate(aggregate: PrimitiveTuple): String = {
    aggregate
      .map(render)
      .mkString("\n")
  }

  def render(field: Field): String = {
    s"${field.name}: ${render(field.typeId)}"
  }

  def render(field: PrimitiveField): String = {
    s"${field.name}: ${render(field.typeId)}"
  }

  def render(tpe: DefMethod): String = {
    tpe match {
      case m: RPCMethod =>
        s"def ${m.name}(${render(m.signature.input)}) => (${render(m.signature.output)})"

      case d: DeprecatedRPCMethod =>
        s"def ${d.name}(${d.signature.input.map(render).mkString(", ")}): (${d.signature.output.map(render).mkString(", ")})"
    }
  }

  def render(out: Service.DefMethod.Output): String = {
    out match {
      case o: Service.DefMethod.Output.Usual =>
        render(o.input)
      case o: Service.DefMethod.Output.Algebraic =>
        o.alternatives.map(render).mkString(" | ")
    }
  }
  def render(signature: SimpleStructure): String = {
    Seq(
      signature.concepts.map(render).map(t => s"* $t")
      , signature.fields.map(render)
    ).flatten.mkString(", ")
  }

  def render(typeId: TypeId): String = {
    typeId match {
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
