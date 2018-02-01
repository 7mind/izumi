package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.finaldef.FinalDefinition.{Alias, DTO, Identifier, Interface}
import com.github.pshirshov.izumi.idealingua.model.finaldef.{DomainDefinition, FinalDefinition, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model._

import scala.collection.mutable
import scala.meta._


class Translation(domain: DomainDefinition) {
  protected val typespace = new Typespace(domain)

  protected val packageObjects: mutable.HashMap[ModuleId, mutable.ArrayBuffer[Defn]] = mutable.HashMap[ModuleId, mutable.ArrayBuffer[Defn]]()

  def translate(): Seq[Module] = {
    domain
      .types
      .flatMap(translateDef) ++ packageObjects.map {
      case (id, content) =>
        val code =
          s"""package object ${id.path.last} {
             |${content.map(_.toString()).mkString("\n\n")}
             |}
           """.stripMargin
        Module(id, withPackage(id.path.init, code))
    }
  }

  protected def translateDef(definition: FinalDefinition): Seq[Module] = {
    val defns = definition match {
      case a: Alias =>
        packageObjects.getOrElseUpdate(toModuleId(a), mutable.ArrayBuffer()) ++= renderAlias(a)
        Seq()

      case i: Identifier =>
        renderIdentifier(i)

      case i: Interface =>
        renderInterface(i)

      case d: DTO =>
        renderDto(d)
    }
    toSource(definition, toModuleId(definition), defns)
  }

  protected def renderAlias(i: Alias): Seq[Defn] = {
    Seq(Defn.Type(List(), Type.Name(i.id.name.id), List(), toScalaType(i.target)))
  }

  val idtInit = initFor(classOf[Identifier])
  val idtGenerated = initFor(classOf[IDLGenerated])

  protected def renderIdentifier(i: Identifier): Seq[Defn] = {
    //val scalaIfaces = i.interfaces.map(typespace).toList
    val fields = typespace.fetchFields(i)
    val scalaFields: Seq[ScalaField] = toScala(i, fields)
    val decls = scalaFields.map {
      f =>
        Term.Param(List(), f.name, Some(f.declType), None)
    }

    val suprerClasses = if (fields.lengthCompare(1) == 0) {
      List(
        Init(Type.Name("AnyVal"), Name.Anonymous(), List())
      )
    } else {
      List(idtGenerated, idtInit)
    }

    //val exploded = explode(i)
    // TODO: contradictions

    val typeName = i.id.name.id

    val interp = Term.Interpolate(Term.Name("s"), List(Lit.String(typeName + "#"), Lit.String("")), List(q"""this.productIterator.map(part => Identifier.escape(part.toString)).mkString(":")"""))
    val classDefs = List(
      q"""override def toString: String = {$interp}"""
    )

    Seq(Defn.Class(
      List(Mod.Case())
      , Type.Name(typeName)
      , List()
      , Ctor.Primary(List(), Name.Anonymous(), List(decls.toList))
      , Template(List(), suprerClasses, Self(Name.Anonymous(), None), classDefs)
    ), Defn.Object(
      List()
      , Term.Name(typeName)
      , Template(List(), List(), Self(Name.Anonymous(), None), List())
    ))
  }


  private def initFor(idtClass: Class[_]) = {
    Init(toSelect(UserType(idtClass.getPackage.getName.split('.'), TypeName(idtClass.getSimpleName))), Name.Anonymous(), List())
  }

  protected def renderInterface(i: Interface): Seq[Defn] = {
    val fields = typespace.fetchFields(i)
    val scalaFields: Seq[ScalaField] = toScala(i, fields)

    val typeName = i.id.name.id

    // TODO: contradictions
    val decls = scalaFields.map {
      f =>
        Decl.Def(List(), f.name, List(), List(), f.declType)
    }

    val scalaIfaces = i.interfaces.map(typespace.apply).toList
    val ifDecls = idtGenerated +: scalaIfaces.map {
      iface =>
        Init(toScalaType(iface.id), Name.Anonymous(), List())
    }

    Seq(Defn.Trait(
      List(),
      Type.Name(typeName),
      List(),
      Ctor.Primary(List(), Name.Anonymous(), List()),
      Template(List(), ifDecls, Self(Name.Anonymous(), None), decls.toList)
    ))
  }

  protected def renderDto(i: DTO): Seq[Defn] = {
    val scalaIfaces = i.interfaces.map(typespace.apply).toList
    val fields = typespace.fetchFields(i)
    val scalaFields: Seq[ScalaField] = toScala(i, fields)
    val decls = scalaFields.map {
      f =>
        Term.Param(List(), f.name, Some(f.declType), None)
    }

    val ifDecls = scalaIfaces.map {
      iface =>
        Init(toScalaType(iface.id), Name.Anonymous(), List())
    }

    // TODO: contradictions

    val suprerClasses = if (fields.lengthCompare(1) == 0) {
      ifDecls :+ Init(Type.Name("AnyVal"), Name.Anonymous(), List())
    } else {
      ifDecls
    }

    val typeName = i.id.name.id

    Seq(Defn.Class(
      List(Mod.Case())
      , Type.Name(typeName)
      , List()
      , Ctor.Primary(List(), Name.Anonymous(), List(decls.toList))
      , Template(List(), suprerClasses, Self(Name.Anonymous(), None), List())
    ))
  }

  private def toModuleId(defn: FinalDefinition) = {
    defn match {
      case i: Alias =>
        ModuleId(i.id.pkg.init, s"${i.id.pkg.last}.scala")

      case other =>
        val id = other.id
        ModuleId(id.pkg, s"${id.name.id}.scala")
    }
  }

  private def toSource(i: FinalDefinition, moduleId: ModuleId, traitDef: Seq[Defn]) = {
    val code = traitDef.map(_.toString()).mkString("\n\n")
    val content: String = withPackage(i.id.pkg, code)
    Seq(Module(moduleId, content))
  }

  private def withPackage(pkg: idealingua.model.Package, code: String) = {
    val content = if (pkg.isEmpty) {
      code
    } else {
      s"""package ${pkg.mkString(".")}
         |
         |$code
       """.stripMargin
    }
    content
  }

  protected def toScala(i: FinalDefinition, fields: Seq[Field]): Seq[ScalaField] = {
    val conflictingFields = fields.groupBy(_.name).filter(_._2.lengthCompare(1) > 0)
    if (conflictingFields.nonEmpty) {
      throw new IDLException(s"Conflicting fields in $i: $conflictingFields")
    }

    fields.map(toScala)
  }


  protected def toScala(field: Field): ScalaField = {
    ScalaField(Term.Name(field.name), toScalaType(field.typeId))
  }

  protected def toScalaType(typeId: TypeId): Type = {
    typeId match {
      case TypeId.TString =>
        t"String"
      case TypeId.TInt32 =>
        t"Int"
      case TypeId.TInt64 =>
        t"Long"

      case _ =>
        val typedef = typespace(typeId)
        toSelect(typedef.id)
    }
  }

  def toSelect(id: TypeId): Type = {
    val maybeSelect: Option[Term.Ref] = id.pkg.headOption.map {
      head =>
        id.pkg.tail.map(v => Term.Select(_: Term, Term.Name(v))).foldLeft(Term.Name(head): Term.Ref) {
          case (acc, v) =>
            v(acc)
        }

    }
    maybeSelect match {
      case Some(v) =>
        Type.Select(v, Type.Name(id.name.id))

      case None =>
        Type.Name(id.name.id)
    }
  }

}
