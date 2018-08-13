package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{DefMethod, TypeDef, _}

class IDLRenderer(domain: DomainDefinition) {
  def render(): String = {
    val sb = new StringBuffer()
    sb.append(s"domain ${render(domain.id)}")

    sb.append("\n\n")
    sb.append(domain.referenced.keys.map(renderImport).mkString("\n"))

    sb.append("\n\n")
    sb.append(domain.types.map(render).map(_.trim).mkString("\n\n"))

    sb.append("\n\n")
    sb.append(domain.services.map(render).map(_.trim).mkString("\n\n"))

    sb.append("\n\n")
    sb.append(domain.emitters.map(render).map(_.trim).mkString("\n\n"))

    sb.append("\n\n")
    sb.append(domain.streams.map(render).map(_.trim).mkString("\n\n"))

    sb.toString
  }

  def renderImport(domain: DomainId): String = {
    s"import ${render(domain)}"
  }

  def render(tpe: TypeDef): String = {
    val struct = tpe match {
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
    withComment(tpe.meta, struct)
  }

  def renderValue(value: Any): String = {
    value match {
      case l: Seq[_] =>
        l.map(renderValue).mkString("[", ",", "]")
      case l: Map[_, _] =>
        l.map {
          case (name, v) =>
            s"$name = ${renderValue(v)}"
        }.mkString("{", ",", "}")
      case s: String =>
        if (s.contains("\"")) {
          "\"\"\"" + s + "\"\"\""
        } else {
          "\"" + s + "\""
        }
      case o =>
        o.toString
    }
  }

  def render(anno: Anno): String = {
    val vals = anno.values.map {
      case (name, v) =>
        s"$name = ${renderValue(v)}"
    }.mkString(",")

    s"@${anno.name}($vals)"
  }

  def withComment(meta: NodeMeta, struct: String): String = {
    val maybeDoc = meta.doc.map {
      d =>
        s"""/*${d.split('\n').map(v => s"  *$v").mkString("\n").trim}
           |  */""".stripMargin
    }

    val maybeAnno = meta.annos.map(render)

    Seq(
      maybeDoc.toSeq
      , maybeAnno
      , Seq(struct)
    )
      .flatten
      .mkString("\n")
  }

  def renderStruct(struct: Structure): String = {
    Seq(
      renderComposite(struct.superclasses.interfaces, "& ")
      , renderComposite(struct.superclasses.concepts, "+ ")
      , renderComposite(struct.superclasses.removedConcepts, "- ")
      , renderAggregate(struct.fields, "")
      , renderAggregate(struct.removedFields, "- ")
    )
      .filterNot(_.isEmpty)
      .mkString("\n")
  }

  def render(service: Service): String = {
    val out =
      s"""service ${render(service.id)} {
         |${service.methods.map(render("def", _)).mkString("\n").shift(2)}
         |}
     """.stripMargin
    withComment(service.doc, out)
  }

  def render(emitter: Emitter): String = {
    val out =
      s"""emitter ${render(emitter.id)} {
         |${emitter.events.map(render("event", _)).mkString("\n").shift(2)}
         |}
     """.stripMargin
    withComment(emitter.doc, out)
  }

  def render(streams: Streams): String = {
    val out =
      s"""streams ${render(streams.id)} {
         |${streams.streams.map(render).mkString("\n").shift(2)}
         |}
     """.stripMargin
    withComment(streams.doc, out)
  }

  def render(streamDirection: StreamDirection): String = {
    streamDirection match {
      case StreamDirection.ToServer =>
        "toserver"
      case StreamDirection.ToClient =>
        "toclient"
    }
  }


  def renderComposite(aggregate: Structures, prefix: String): String = {
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

  def renderPrimitiveAggregate(aggregate: IdTuple): String = {
    aggregate
      .map(render)
      .mkString("\n")
  }

  def render(field: AdtMember): String = {
    val t = render(field.typeId)
    field.memberName match {
      case Some(name) =>
        s"$t as $name"
      case None =>
        t
    }
  }

  def render(field: Field): String = {
    s"${field.name}: ${render(field.typeId)}"
  }

  def render(field: IdField): String = {
    s"${field.name}: ${render(field.typeId)}"
  }

  def render(tpe: TypedStream): String = {
    tpe match {
      case m: TypedStream.Directed =>
        val out = s"${render(m.direction)} ${m.name}(${render(m.signature)})"
        withComment(m.meta, out)
    }
  }

  def render(kw: String, tpe: DefMethod): String = {
    tpe match {
      case m: RPCMethod =>
        val resultRepr = render(m.signature.output).fold("")(s => s": $s")
        val out = s"$kw ${m.name}(${render(m.signature.input)})$resultRepr"
        withComment(m.meta, out)
    }
  }

  def render(out: DefMethod.Output): Option[String] = {
    out match {
      case o: DefMethod.Output.Struct =>
        Some(s"(${render(o.struct)})")
      case o: DefMethod.Output.Algebraic =>
        Some(s"(${o.alternatives.map(render).mkString(" | ")})")
      case o: DefMethod.Output.Singular =>
        Some(render(o.typeId))
      case _: DefMethod.Output.Void =>
        None
      case o: DefMethod.Output.Alternative =>
        Some(s"${render(o.success)} !! ${o.failure}")
    }
  }

  def render(signature: SimpleStructure): String = {
    Seq(
      signature.concepts.map(render).map(t => s"+ $t")
      , signature.fields.map(render)
    ).flatten.mkString(", ")
  }

  def render(typeId: TypeId): String = {
    typeId match {
      case g: Generic =>
        s"${renderTypeName(g.path, g.name)}${g.args.map(render).mkString("[", ", ", "]")}"

      case t =>
        renderTypeName(t.path, t.name)
    }
  }

  private def renderTypeName(pkg: TypePath, name: TypeName) = {
    pkg.domain match {
      case DomainId.Builtin =>
        name

      case _ =>
        Seq(renderPkg(pkg.toPackage), name).filterNot(_.isEmpty).mkString("#")
    }
  }

  def renderPkg(value: common.Package): String = {
    minimize(value).mkString(".")
  }

  def render(id: ServiceId): String = {
    id.name
  }

  def render(id: EmitterId): String = {
    id.name
  }

  def render(id: StreamsId): String = {
    id.name
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
    domainId.toPackage.mkString(".")
  }

  def renderString(s: String): String = {
    s // TODO: escape
  }
}
