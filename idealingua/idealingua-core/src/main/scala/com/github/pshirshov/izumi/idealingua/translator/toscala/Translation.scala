package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.finaldef.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.finaldef.FinalDefinition._
import com.github.pshirshov.izumi.idealingua.model.finaldef.{DomainDefinition, FinalDefinition, Service, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.runtime._

import scala.collection.mutable
import scala.meta._


class Translation(domain: DomainDefinition) {
  protected val typespace = new Typespace(domain)
  protected val conv = new ScalaTypeConverter(typespace)

  import conv._

  final val idtGenerated = conv.toScala[IDLGenerated].init()
  final val idtService = conv.toScala[IDLService].init()
  final val inputInit = conv.toScala[IDLInput].init()
  final val outputInit = conv.toScala[IDLOutput].init()
  final val typeCompanionInit = conv.toScala[IDLTypeCompanion].init()
  final val enumInit = conv.toScala[IDLEnum].init()
  final val enumElInit = conv.toScala[IDLEnumElement].init()
  final val serviceCompanionInit = conv.toScala[IDLServiceCompanion].init()

  final val tAbstractTransport = conv.toScala[AbstractTransport[_]]
  final val tIDLIdentifier = conv.toScala[IDLIdentifier]
  final val tFinalDefinition = conv.toScala[FinalDefinition]
  final val tDomainCompanion = conv.toScala[IDLDomainCompanion]
  final val tDomainDefinition = conv.toScala[DomainDefinition]
  final val tService = conv.toScala[Service]

  final val tDomain = conv.toScala(JavaType(Seq("izumi", "idealingua", "domains"), domain.id.capitalize))

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
      domain.services.flatMap(translateService) ++
      translateDomain()
  }

  protected def translateDomain(): Seq[Module] = {
    toSource(tDomain.javaType.pkg, ModuleId(tDomain.javaType.pkg, domain.id), Seq(
      q"""object ${tDomain.termName} extends ${tDomainCompanion.init()} {
                override final lazy val domain: ${tDomainDefinition.tpe} = {
                ${SchemaSerializer.toAst(domain)}
              }
             }"""
    ))

  }

  protected def translateService(definition: Service): Seq[Module] = {
    toSource(definition.id.pkg, toModuleId(definition.id), renderService(definition))
  }


  protected def translateDef(definition: FinalDefinition): Seq[Module] = {
    val defns = definition match {
      case a: Alias =>
        packageObjects.getOrElseUpdate(toModuleId(a), mutable.ArrayBuffer()) ++= renderAlias(a)
        Seq()

      case i: Enumeration =>
        renderEnumeration(i)

      case i: Identifier =>
        renderIdentifier(i)

      case i: Interface =>
        renderInterface(i)

      case d: DTO =>
        renderDto(d)
    }
    if (defns.nonEmpty) {
      toSource(definition.id.pkg, toModuleId(definition), defns)
    } else {
      Seq.empty
    }
  }

  def renderEnumeration(i: Enumeration): Seq[Defn] = {
    val t = conv.toScala(i.id)

    val duplicates = i.members.groupBy(v => v).filter(_._2.lengthCompare(1) > 0)
    if (duplicates.nonEmpty) {
      throw new IDLException(s"Duplicated enum elements: $duplicates")
    }

    val members = i.members.map {
      m =>
        val mt = Term.Name(m)
        q"""case object $mt extends ${t.init()} {
              override def toString: String = ${Lit.String(m)}
            }"""
    }

    Seq(
      q""" sealed trait ${t.typeName} extends $enumElInit {} """
      ,
      q"""object ${t.termName} extends $enumInit {
            type Element = ${t.tpe}

            override def all: Seq[${t.tpe}] = Seq(..${members.map(_.name)})

            ..$members

           }"""
    )
  }

  protected def renderAlias(i: Alias): Seq[Defn] = {
    Seq(Defn.Type(List.empty, Type.Name(i.id.name), List.empty, conv.toScalaType(i.target)))
  }

  protected def renderIdentifier(i: Identifier): Seq[Defn] = {
    val fields = typespace.fetchFields(i)
    val decls = fields.toScala.map {
      f =>
        Term.Param(List.empty, f.name, Some(f.declType), None)
    }

    val superClasses = if (fields.lengthCompare(1) == 0) {
      List(
        Init(Type.Name("AnyVal"), Name.Anonymous(), List.empty)
        , idtGenerated
        , tIDLIdentifier.init()
      )
    } else {
      List(idtGenerated, tIDLIdentifier.init())
    }

    // TODO: contradictions

    val typeName = i.id.name

    val interp = Term.Interpolate(Term.Name("s"), List(Lit.String(typeName + "#"), Lit.String("")), List(Term.Name("suffix")))

    val t = conv.toScala(i.id)

    Seq(
      q"""case class ${t.typeName} (..$decls) extends ..$superClasses {
            override def toString: String = {
              val suffix = this.productIterator.map(part => ${tIDLIdentifier.term}.escape(part.toString)).mkString(":")
              $interp
            }

            override def companion: ${t.term}.type = ${t.term}
         }"""
      ,
      q"""object ${Term.Name(typeName)} extends $typeCompanionInit {
             override final lazy val definition: ${tFinalDefinition.tpe} = {
              ${SchemaSerializer.toAst(i)}
             }

             override final def domain: ${tDomainCompanion.tpe} = {
              ${tDomain.term}
             }
         }"""
    )
  }

  protected def renderInterface(i: Interface): Seq[Defn] = {
    val fields = typespace.fetchFields(i)
    val scalaFields: Seq[ScalaField] = fields.toScala


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

    val t = conv.toScala(i.id)
    val dtoName = toDtoName(i.id)
    val impl = renderComposite(t.within(dtoName).javaType, Seq(i.id), List.empty).toList

    val parents = typespace.implements(i.id)
    val narrowers = parents.map {
      p =>
        val ifields = typespace.enumFields(typespace(p))

        val constructorCode = ifields.map {
          f =>
            q""" ${Term.Name(f.field.name)} = this.${Term.Name(f.field.name)}  """
        }

        q"""def ${Term.Name("to" + p.name.capitalize)}(): ${t.term}.${Type.Name(toDtoName(p))} = {
             ${t.term}.${Term.Name(toDtoName(p))}(..$constructorCode)
            }
          """
    }

    val allDecls = decls ++ narrowers

    Seq(
      q"""trait ${t.typeName} extends ..$ifDecls {
          override def companion: ${t.term}.type = ${t.term}

          ..$allDecls
          }

       """
      ,
      q"""object ${t.termName} extends $typeCompanionInit {
             override final lazy val definition: ${tFinalDefinition.tpe} = {
              ${SchemaSerializer.toAst(i)}
             }

             override final def domain: ${tDomainCompanion.tpe} = {
              ${tDomain.term}
             }

             ..$impl
         }"""
    )
  }


  protected def renderService(i: Service): Seq[Defn] = {
    val typeName = i.id.name

    case class ServiceMethodProduct(defn: Stat, routingClause: Case, types: Seq[Defn])

    val t = conv.toScala(i.id)

    val serviceInputBase = t.within(s"In${typeName.capitalize}")
    val serviceOutputBase = t.within(s"Out${typeName.capitalize}")

    val decls = i.methods.toList.map {
      case method: RPCMethod =>
        val inName = t.within(s"In${method.name.capitalize}")
        val outName = t.within(s"Out${method.name.capitalize}")

        val inputComposite = renderComposite(inName.javaType, method.signature.input, List(serviceInputBase.init()))
        val outputComposite = renderComposite(outName.javaType, method.signature.output, List(serviceOutputBase.init()))

        val inputType = inName.tpe
        val outputType = outName.tpe

        ServiceMethodProduct(
          q"def ${Term.Name(method.name)}(input: $inputType): $outputType"
          , Case(
            Pat.Typed(Pat.Var(Term.Name("value")), inputType)
            , None
            , q"service.${Term.Name(method.name)}(value)"
          )
          , inputComposite ++ outputComposite
        )
    }

    val forwarder = Term.Match(Term.Name("input"), decls.map(_.routingClause))

    val transportDecls = List(
      q"override def process(input: ${idtGenerated.tpe}): ${idtGenerated.tpe} = $forwarder"
    )
    val abstractTransportTpe = tAbstractTransport.init(List(t.tpe))

    Seq(
      q"""trait ${t.typeName} extends $idtService {
          import ${t.term}._

          override def companion: ${t.term}.type = ${t.term}

          ..${decls.map(_.defn)}
         }"""
      ,
      q"""class ${Type.Name(typeName + "AbstractTransport")}
            (
              override val service: ${t.tpe}
            ) extends $abstractTransportTpe {
            import ${t.term}._

            ..$transportDecls
           }"""
      ,
      q"""object ${t.termName} extends $serviceCompanionInit {
            trait ${serviceInputBase.typeName} extends $inputInit {}
            trait ${serviceOutputBase.typeName} extends $outputInit {}

            override type InputType = ${serviceInputBase.tpe}
            override type OutputType = ${serviceOutputBase.tpe}

            override def inputTag: scala.reflect.ClassTag[${serviceInputBase.tpe}] = scala.reflect.classTag[${serviceInputBase.tpe}]
            override def outputTag: scala.reflect.ClassTag[${serviceOutputBase.tpe}] = scala.reflect.classTag[${serviceOutputBase.tpe}]


            override final lazy val schema: ${tService.tpe} = {
              ${SchemaSerializer.toAst(i)}
            }
            override final def domain: ${tDomainCompanion.tpe} = {
              ${tDomain.term}
            }

            ..${decls.flatMap(_.types)}
           }"""
    )
  }

  protected def renderDto(i: DTO): Seq[Defn] = {
    renderComposite(JavaType(i.id), i.interfaces, List.empty)
  }

  private def renderComposite(typeName: JavaType, interfaces: Composite, bases: List[Init]): Seq[Defn] = {
    val fields = typespace.enumFields(interfaces)
    val scalaIfaces = interfaces.map(typespace.apply).toList
    val scalaFields: Seq[ScalaField] = fields.toScala
    val decls = scalaFields.toList.map {
      f =>
        Term.Param(List.empty, f.name, Some(f.declType), None)
    }

    val ifDecls = scalaIfaces.map {
      iface =>
        Init(conv.toScalaType(iface.id), Name.Anonymous(), List.empty)
    }

    // TODO: contradictions

    val superClasses = bases ++ (if (fields.lengthCompare(1) == 0) {
      ifDecls :+ Init(Type.Name("AnyVal"), Name.Anonymous(), List.empty)
    } else {
      ifDecls
    })

    val t = conv.toScala(typeName)

    val constructorSignature = scalaIfaces.map(d => Term.Param(List.empty, definitionToParaName(d), Some(conv.toScalaType(d.id)), None))
    val constructorCode = fields.map {
      f =>
        q""" ${Term.Name(f.field.name)} = ${idToParaName(f.definedBy)}.${Term.Name(f.field.name)}  """
    }


    val constructors = List(
      q"""def apply(..$constructorSignature): ${t.tpe} = {
         ${t.term}(..$constructorCode)
         }"""
    )

    Seq(
      q"""case class ${t.typeName}(..$decls) extends ..$superClasses {

          }
       """
      ,
      q"""object ${t.termName} extends $typeCompanionInit {
             override final lazy val definition: ${tFinalDefinition.tpe} = {
                ???
             }

             override final def domain: ${tDomainCompanion.tpe} = {
              ${tDomain.term}
             }

             ..$constructors
         }"""
    )

    //${SchemaSerializer.toAst(i)}
  }

  private def toDtoName(id: TypeId) = {
    id match {
      case _: InterfaceId =>
        s"${id.name}Impl"
      case _ =>
        s"${id.name}"

    }
  }

  private def definitionToParaName(d: FinalDefinition) = idToParaName(d.id)

  private def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)


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

  private def toSource(pkg: Package, moduleId: ModuleId, traitDef: Seq[Defn]) = {
    val code = traitDef.map(_.toString()).mkString("\n\n")
    val content: String = withPackage(pkg, code)
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


}

