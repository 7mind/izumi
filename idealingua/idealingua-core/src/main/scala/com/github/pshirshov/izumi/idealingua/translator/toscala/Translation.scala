package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.finaldef.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.finaldef.FinalDefinition._
import com.github.pshirshov.izumi.idealingua.model.finaldef.{DomainDefinition, FinalDefinition, Service, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.runtime.{AbstractTransport, IDLGenerated, IDLIdentifier, IDLService}

import scala.collection.mutable
import scala.meta._


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
    val fields = typespace.fetchFields(i)
    val decls = toScala(fields).map {
      f =>
        Term.Param(List.empty, f.name, Some(f.declType), None)
    }

    val superClasses = if (fields.lengthCompare(1) == 0) {
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

    Seq(
      q"case class ${Type.Name(typeName)}(..$decls) extends ..$superClasses { ..$classDefs }"
      , q"object ${Term.Name(typeName)} { }"
    )
  }

  protected def renderInterface(i: Interface): Seq[Defn] = {
    val fields = typespace.fetchFields(i)
    val scalaFields: Seq[ScalaField] = toScala(fields)

    val typeName = i.id.name

    // TODO: contradictions
    val decls = scalaFields.toList.map {
      f =>
        Decl.Def(List.empty, f.name, List.empty, List.empty, f.declType)
    }

    val scalaIfaces = i.interfaces.map(typespace.apply).toList
    val ifDecls = idtGenerated +: scalaIfaces.map {
      iface =>
        Init(conv.toScalaType(iface.id), Name.Anonymous(), List.empty)
    }

    Seq(q"trait ${Type.Name(typeName)} extends ..$ifDecls { ..$decls}"
    )
  }

  protected def renderService(i: Service): Seq[Defn] = {
    val typeName = i.id.name

    case class ServiceMethodProduct(defn: Stat, routingClause: Case, types: Seq[Defn])

    val decls = i.methods.toList.map {
      case method: RPCMethod =>
        val inName = s"In${method.name.capitalize}"
        val outName = s"Out${method.name.capitalize}"

        val inputComposite = renderComposite(inName, method.signature.input)
        val outputComposite = renderComposite(outName, method.signature.output)

        val inputType = Type.Name(inName)
        val outputType = Type.Name(outName)

        ServiceMethodProduct(
          q"def ${Term.Name(method.name)}(input: $inputType): $outputType"
          , Case(
            Pat.Typed(Pat.Var(Term.Name("value")), inputType)
            , None
            , Term.Apply(Term.Select(Term.Name("service"), Term.Name(method.name)), List(Term.Name("value")))
          )
          , inputComposite ++ outputComposite
        )
    }

    val forwarder = Term.Match(Term.Name("input"), decls.map(_.routingClause))
    val tpe = Type.Name(typeName)
    val tpet = Term.Name(typeName)

    val transportDecls = List(
      q"override def process(input: ${idtGenerated.tpe}): ${idtGenerated.tpe} = $forwarder"
    )

    Seq(
      q"""trait $tpe extends $idtService {
          import $tpet._

          ..${decls.map(_.defn)}
         }"""
      ,
      q"""class ${Type.Name(typeName + "AbstractTransport")}
            (
              override val service: $tpe
            ) extends ${conv.init[AbstractTransport[_]](List(tpe))} {
            import $tpet._

            ..$transportDecls
           }"""
      , q"object ${Term.Name(typeName)} { ..${decls.flatMap(_.types)} }"
    )
  }

  protected def renderDto(i: DTO): Seq[Defn] = {
    val typeName = i.id.name
    val interfaces = i.interfaces
    renderComposite(typeName, interfaces)
  }

  private def renderComposite(typeName: TypeName, interfaces: Composite) = {
    val fields = typespace.fetchFields(interfaces)
    val scalaIfaces = interfaces.map(typespace.apply).toList
    val scalaFields: Seq[ScalaField] = toScala(fields)
    val decls = scalaFields.toList.map {
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


    Seq(
      q"case class ${Type.Name(typeName)}(..$decls) extends ..$suprerClasses"
    )
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

  protected def toScala(fields: Seq[Field]): List[ScalaField] = {
    val conflictingFields = fields.groupBy(_.name).filter(_._2.lengthCompare(1) > 0)
    if (conflictingFields.nonEmpty) {
      throw new IDLException(s"Conflicting fields: $conflictingFields")
    }

    fields.map(toScala).toList
  }


  protected def toScala(field: Field): ScalaField = {
    ScalaField(Term.Name(field.name), conv.toScalaType(field.typeId))
  }


}

