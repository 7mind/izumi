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
  final val idtInit = initFor[IDLIdentifier]
  final val idtGenerated = initFor[IDLGenerated]
  final val idtService = initFor[IDLService]

  protected val typespace = new Typespace(domain)

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
    Seq(Defn.Type(List.empty, Type.Name(i.id.name.id), List.empty, toScalaType(i.target)))
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

    val typeName = i.id.name.id

    val idt = toSelectTerm(JavaType.get[IDLIdentifier])
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

    val typeName = i.id.name.id

    // TODO: contradictions
    val decls = scalaFields.map {
      f =>
        Decl.Def(List.empty, f.name, List.empty, List.empty, f.declType)
    }

    val scalaIfaces = i.interfaces.map(typespace.apply).toList
    val ifDecls = idtGenerated +: scalaIfaces.map {
      iface =>
        Init(toScalaType(iface.id), Name.Anonymous(), List.empty)
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
    val typeName = i.id.name.id

    // TODO: contradictions
    val decls = i.methods.map {
      case method: RPCMethod =>
        q"def ${Term.Name(method.name)}(input: ${toScalaType(method.input)}): ${toScalaType(method.output)}"
    }


    val forwarderCases = i.methods.toList.map {
      case method: RPCMethod =>
        Case(Pat.Typed(Pat.Var(Term.Name("value"))
          , toScalaType(method.input))
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
        Ctor.Primary(List.empty, Name.Anonymous(), List(List(Term.Param(List(Mod.Override(), Mod.ValParam()), Term.Name("service"), Some(tpe), None))) ),
        Template(List.empty, List(init[AbstractTransport[_]](List(tpe))), Self(Name.Anonymous(), None), transportDecls)
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
        Init(toScalaType(iface.id), Name.Anonymous(), List.empty)
    }

    // TODO: contradictions

    val suprerClasses = if (fields.lengthCompare(1) == 0) {
      ifDecls :+ Init(Type.Name("AnyVal"), Name.Anonymous(), List.empty)
    } else {
      ifDecls
    }

    val typeName = i.id.name.id

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
    ModuleId(id.pkg, s"${id.name.id}.scala")
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
        toSelect(typedef.id.toJava)
    }
  }


  def toSelectTerm(id: JavaType): Term = {
    val maybeSelect: Option[Term.Ref] = id.pkg.headOption.map {
      head =>
        id.pkg.tail.map(v => Term.Select(_: Term, Term.Name(v))).foldLeft(Term.Name(head): Term.Ref) {
          case (acc, v) =>
            v(acc)
        }

    }
    maybeSelect match {
      case Some(v) =>
        Term.Select(v, Term.Name(id.name))

      case None =>
        Term.Name(id.name)
    }
  }

  def toSelect(id: JavaType): Type = {
    val maybeSelect: Option[Term.Ref] = id.pkg.headOption.map {
      head =>
        id.pkg.tail.map(v => Term.Select(_: Term, Term.Name(v))).foldLeft(Term.Name(head): Term.Ref) {
          case (acc, v) =>
            v(acc)
        }

    }
    maybeSelect match {
      case Some(v) =>
        Type.Select(v, Type.Name(id.name))

      case None =>
        Type.Name(id.name)
    }
  }

  private def initFor[T: ClassTag] = {
    init[T](List.empty)
  }

  private def init[T: ClassTag](typeArgs: List[Type], constructorArgs: Term*) = {
    val idtClass = classTag[T].runtimeClass
    val select = toSelect(UserType(idtClass.getPackage.getName.split('.'), TypeName(idtClass.getSimpleName)).toJava)

    val cargs = if (constructorArgs.isEmpty) {
      List.empty
    } else {
      List(constructorArgs.toList)
    }

    if (typeArgs.isEmpty) {
      Init(select, Name.Anonymous(), cargs)
    } else {
      Init(Type.Apply(select, typeArgs), Name.Anonymous(), cargs)
    }
  }
}
