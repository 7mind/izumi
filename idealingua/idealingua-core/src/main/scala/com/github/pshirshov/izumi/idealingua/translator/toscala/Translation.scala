package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.finaldef.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.finaldef.FinalDefinition.{Alias, DTO, Identifier, Interface}
import com.github.pshirshov.izumi.idealingua.model.finaldef.{DomainDefinition, FinalDefinition, Service, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.runtime.{AbstractTransport, IDLGenerated, IDLIdentifier, IDLService}

import scala.collection.mutable
import scala.meta._
import scala.reflect._


class Translation(domain: DomainDefinition) {
  protected val typespace = new Typespace(domain)
  protected val conv = new ScalaTypeConverter(typespace)

  final val idtInit = conv.initFor[IDLIdentifier]
  final val idtGenerated = conv.initFor[IDLGenerated]
  final val idtService = conv.initFor[IDLService]

  protected val packageObjects: mutable.HashMap[ModuleId, mutable.ArrayBuffer[Defn]] = mutable.HashMap[ModuleId, mutable.ArrayBuffer[Defn]]()

  def translate(): Seq[Module] = {
    domain
      .types
      .flatMap(translateDef) ++
      packageObjects.map {
        case (id, content) =>
          // TODO: dirty!
          val pkgName = id.name.split('.').head

          val code =
            s"""
               |package object $pkgName {
               |${content.map(_.toString()).mkString("\n\n")}
               |}
           """.stripMargin
          Module(id, withPackage(id.path.init, code))
      } ++
      domain.services.flatMap(translateService)
  }

  protected def translateService(definition: Service): Seq[Module] = {
    toSource(definition.id, toModuleId(definition.id), renderService(definition))
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
    if (defns.nonEmpty) {
      toSource(definition.id, toModuleId(definition), defns)
    } else {
      Seq.empty
    }
  }

  protected def renderAlias(i: Alias): Seq[Defn] = {
    Seq(Defn.Type(List.empty, Type.Name(i.id.name), List.empty, conv.toScalaType(i.target)))
  }

  protected def renderIdentifier(i: Identifier): Seq[Defn] = {
    //val scalaIfaces = i.interfaces.map(typespace).toList
    val fields = typespace.fetchFields(i)
    val scalaFields: Seq[ScalaField] = toScala(i, fields)
    val decls = scalaFields.map {
      f =>
        Term.Param(List.empty, f.name, Some(f.declType), None)
    }

    val suprerClasses = if (fields.lengthCompare(1) == 0) {
      List(
        Init(Type.Name("AnyVal"), Name.Anonymous(), List.empty)
      )
    } else {
      List(idtGenerated, idtInit)
    }

    //val exploded = explode(i)
    // TODO: contradictions

    val typeName = i.id.name

    val idt = conv.toSelectTerm(JavaType.get[IDLIdentifier])
    val interp = Term.Interpolate(Term.Name("s"), List(Lit.String(typeName + "#"), Lit.String("")), List(Term.Name("suffix")))
    val classDefs = List(
      q"""override def toString: String = {
         val suffix = this.productIterator.map(part => $idt.escape(part.toString)).mkString(":")
         $interp
         }"""
    )

    Seq(Defn.Class(
      List(Mod.Case())
      , Type.Name(typeName)
      , List.empty
      , Ctor.Primary(List.empty, Name.Anonymous(), List(decls.toList))
      , Template(List.empty, suprerClasses, Self(Name.Anonymous(), None), classDefs)
    ), Defn.Object(
      List.empty
      , Term.Name(typeName)
      , Template(List.empty, List.empty, Self(Name.Anonymous(), None), List.empty)
    ))
  }

  protected def renderInterface(i: Interface): Seq[Defn] = {
    val fields = typespace.fetchFields(i)
    val scalaFields: Seq[ScalaField] = toScala(i, fields)

    val typeName = i.id.name

    // TODO: contradictions
    val decls = scalaFields.map {
      f =>
        Decl.Def(List.empty, f.name, List.empty, List.empty, f.declType)
    }

    val scalaIfaces = i.interfaces.map(typespace.apply).toList
    val ifDecls = idtGenerated +: scalaIfaces.map {
      iface =>
        Init(conv.toScalaType(iface.id), Name.Anonymous(), List.empty)
    }

    Seq(Defn.Trait(
      List.empty,
      Type.Name(typeName),
      List.empty,
      Ctor.Primary(List.empty, Name.Anonymous(), List.empty),
      Template(List.empty, ifDecls, Self(Name.Anonymous(), None), decls.toList)
    ))
  }

  protected def renderService(i: Service): Seq[Defn] = {
    val typeName = i.id.name

    // TODO: contradictions
    val decls = i.methods.map {
      case method: RPCMethod =>
        q"def ${Term.Name(method.name)}(input: ${conv.toScalaType(method.input)}): ${conv.toScalaType(method.output)}"
    }


    val forwarderCases = i.methods.toList.map {
      case method: RPCMethod =>
        val tpe = conv.toScalaType(method.input)
        typespace(method.input) match {
          case _: DTO =>

          case _ =>
            ???
        }
        Case(
          Pat.Typed(Pat.Var(Term.Name("value")), tpe)
          , None
          , Term.Apply(Term.Select(Term.Name("service"), Term.Name(method.name)), List(Term.Name("value")))
        )
    }

    val forwarder = Term.Match(Term.Name("input"), forwarderCases)

    val transportDecls = List(
      q"override def process(input: ${idtGenerated.tpe}): ${idtGenerated.tpe} = $forwarder"
    )

    val tpe = Type.Name(typeName)
    Seq(
      Defn.Trait(
        List.empty,
        tpe,
        List.empty,
        Ctor.Primary(List.empty, Name.Anonymous(), List.empty),
        Template(List.empty, List(idtService), Self(Name.Anonymous(), None), decls.toList)
      )
      , Defn.Class(
        List.empty,
        Type.Name(typeName + "AbstractTransport"),
        List.empty,
        Ctor.Primary(List.empty, Name.Anonymous(), List(List(Term.Param(List(Mod.Override(), Mod.ValParam()), Term.Name("service"), Some(tpe), None)))),
        Template(List.empty, List(conv.init[AbstractTransport[_]](List(tpe))), Self(Name.Anonymous(), None), transportDecls)
      )
    )
  }

  protected def renderDto(i: DTO): Seq[Defn] = {
    val scalaIfaces = i.interfaces.map(typespace.apply).toList
    val fields = typespace.fetchFields(i)
    val scalaFields: Seq[ScalaField] = toScala(i, fields)
    val decls = scalaFields.map {
      f =>
        Term.Param(List.empty, f.name, Some(f.declType), None)
    }

    val ifDecls = scalaIfaces.map {
      iface =>
        Init(conv.toScalaType(iface.id), Name.Anonymous(), List.empty)
    }

    // TODO: contradictions

    val suprerClasses = if (fields.lengthCompare(1) == 0) {
      ifDecls :+ Init(Type.Name("AnyVal"), Name.Anonymous(), List.empty)
    } else {
      ifDecls
    }

    val typeName = i.id.name

    Seq(Defn.Class(
      List(Mod.Case())
      , Type.Name(typeName)
      , List.empty
      , Ctor.Primary(List.empty, Name.Anonymous(), List(decls.toList))
      , Template(List.empty, suprerClasses, Self(Name.Anonymous(), None), List.empty)
    ))
  }

  private def toModuleId(defn: FinalDefinition): ModuleId = {
    defn match {
      case i: Alias =>
        ModuleId(i.id.pkg, s"${i.id.pkg.last}.scala")

      case other =>
        val id = other.id
        toModuleId(id)
    }
  }

  private def toModuleId(id: TypeId): ModuleId = {
    ModuleId(id.pkg, s"${id.name}.scala")
  }

  private def toSource(typeId: TypeId, moduleId: ModuleId, traitDef: Seq[Defn]) = {
    val code = traitDef.map(_.toString()).mkString("\n\n")
    val content: String = withPackage(typeId.pkg, code)
    Seq(Module(moduleId, content))
  }

  private def withPackage(pkg: idealingua.model.common.Package, code: String) = {
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
    ScalaField(Term.Name(field.name), conv.toScalaType(field.typeId))
  }


}

