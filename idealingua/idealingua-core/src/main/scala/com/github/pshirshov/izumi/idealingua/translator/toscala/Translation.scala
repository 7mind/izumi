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

  final val idtInit = conv.toScala[IDLIdentifier].init()
  final val idtGenerated = conv.toScala[IDLGenerated].init()
  final val idtService = conv.toScala[IDLService].init()
  final val inputInit = conv.toScala[IDLInput].init()
  final val outputInit = conv.toScala[IDLOutput].init()
  final val domainCompanionInit = conv.toScala[IDLDomainCompanion].init()
  final val typeCompanionInit = conv.toScala[IDLTypeCompanion].init()
  final val enumInit = conv.toScala[IDLEnum].init()
  final val enumElInit = conv.toScala[IDLEnumElement].init()
  final val serviceCompanionInit = conv.toScala[IDLServiceCompanion].init()

  final val domainCompanionType = JavaType(Seq("izumi", "idealingua", "domains"), domain.id.capitalize)
  final val domainCompanion = Term.Name(domainCompanionType.name)

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
    toSource(domainCompanionType.pkg, ModuleId(domainCompanionType.pkg, domain.id), Seq(
      q"""object $domainCompanion extends $domainCompanionInit {
                override final lazy val domain: ${conv.toSelect(JavaType.get[DomainDefinition])} = {
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
    val typeName = i.id.name
    val tpet = Type.Name(typeName)
    val tpe = Term.Name(typeName)
    val init = Init(tpet, Name.Anonymous(), List.empty)

    val duplicates = i.members.groupBy(v => v).filter(_._2.lengthCompare(1) > 0)
    if (duplicates.nonEmpty) {
      throw new IDLException(s"Duplicated enum elements: $duplicates")
    }

    val members = i.members.map {
      m =>
        val mt = Term.Name(m)
        q"""case object $mt extends $init {
              override def toString: String = ${Lit.String(m)}
            }"""
    }

    Seq(
      q""" sealed trait $tpet extends $enumElInit {} """
      ,
      q"""object $tpe extends $enumInit {
            type Element = $tpet

            override def all: Seq[$tpet] = Seq(..${members.map(_.name)})

            ..$members

           }"""
    )
  }

  protected def renderAlias(i: Alias): Seq[Defn] = {
    Seq(Defn.Type(List.empty, Type.Name(i.id.name), List.empty, conv.toScalaType(i.target)))
  }

  protected def renderIdentifier(i: Identifier): Seq[Defn] = {
    import conv._
    val fields = typespace.fetchFields(i)
    val decls = fields.toScala.map {
      f =>
        Term.Param(List.empty, f.name, Some(f.declType), None)
    }

    val superClasses = if (fields.lengthCompare(1) == 0) {
      List(
        Init(Type.Name("AnyVal"), Name.Anonymous(), List.empty)
        , idtGenerated
        , idtInit
      )
    } else {
      List(idtGenerated, idtInit)
    }

    //val exploded = explode(i)
    // TODO: contradictions

    val typeName = i.id.name

    val idt = conv.toSelectTerm(JavaType.get[IDLIdentifier])
    val interp = Term.Interpolate(Term.Name("s"), List(Lit.String(typeName + "#"), Lit.String("")), List(Term.Name("suffix")))

    val t = conv.toScala(typeName)

    Seq(
      q"""case class ${t.typeName} (..$decls) extends ..$superClasses {
            override def toString: String = {
              val suffix = this.productIterator.map(part => $idt.escape(part.toString)).mkString(":")
              $interp
            }

            override def companion: ${t.term}.type = ${t.term}
         }"""
      ,
      q"""object ${Term.Name(typeName)} extends $typeCompanionInit {
             override final lazy val definition: ${conv.toSelect(JavaType.get[FinalDefinition])} = {
              ${SchemaSerializer.toAst(i)}
             }

             override final def domain: ${conv.toSelect(JavaType.get[IDLDomainCompanion])} = {
              ${conv.toSelectTerm(domainCompanionType)}
             }
         }"""
    )
  }

  protected def renderInterface(i: Interface): Seq[Defn] = {
    import conv._
    val fields = typespace.fetchFields(i)
    val scalaFields: Seq[ScalaField] = fields.toScala

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

    val t = conv.toScala(typeName)
    val impl = renderComposite(toDtoName(i.id), Seq(i.id), List.empty).toList

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
      q"""trait ${Type.Name(typeName)} extends ..$ifDecls {
          override def companion: ${t.term}.type = ${t.term}

          ..$allDecls
          }

       """
      ,
      q"""object ${Term.Name(typeName)} extends $typeCompanionInit {
             override final lazy val definition: ${conv.toSelect(JavaType.get[FinalDefinition])} = {
              ${SchemaSerializer.toAst(i)}
             }

             override final def domain: ${conv.toSelect(JavaType.get[IDLDomainCompanion])} = {
              ${conv.toSelectTerm(domainCompanionType)}
             }

             ..$impl
         }"""
    )
  }

  protected def renderService(i: Service): Seq[Defn] = {
    val typeName = i.id.name

    case class ServiceMethodProduct(defn: Stat, routingClause: Case, types: Seq[Defn])

    val serviceInputBase = Type.Name(s"In${typeName.capitalize}")
    val serviceOutputBase = Type.Name(s"Out${typeName.capitalize}")

    val decls = i.methods.toList.map {
      case method: RPCMethod =>
        val inName = s"In${method.name.capitalize}"
        val outName = s"Out${method.name.capitalize}"

        val inputComposite = renderComposite(inName, method.signature.input, List(Init(serviceInputBase, Name.Anonymous(), List.empty)))
        val outputComposite = renderComposite(outName, method.signature.output, List(Init(serviceOutputBase, Name.Anonymous(), List.empty)))

        val inputType = Type.Name(inName)
        val outputType = Type.Name(outName)

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
    val t = conv.toScala(typeName)

    val transportDecls = List(
      q"override def process(input: ${idtGenerated.tpe}): ${idtGenerated.tpe} = $forwarder"
    )
    val abstractTransportTpe = conv.toScala[AbstractTransport[_]].init(List(t.tpe))

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
            trait $serviceInputBase extends $inputInit {}
            trait $serviceOutputBase extends $outputInit {}

            override type InputType = $serviceInputBase
            override type OutputType = $serviceOutputBase

            override def inputTag: scala.reflect.ClassTag[$serviceInputBase] = scala.reflect.classTag[$serviceInputBase]
            override def outputTag: scala.reflect.ClassTag[$serviceOutputBase] = scala.reflect.classTag[$serviceOutputBase]


            override final lazy val schema: ${conv.toSelect(JavaType.get[Service])} = {
              ${SchemaSerializer.toAst(i)}
            }
            override final def domain: ${conv.toSelect(JavaType.get[IDLDomainCompanion])} = {
              ${conv.toSelectTerm(domainCompanionType)}
            }

            ..${decls.flatMap(_.types)}
           }"""
    )
  }

  protected def renderDto(i: DTO): Seq[Defn] = {
    val typeName = i.id.name
    val interfaces = i.interfaces
    renderComposite(typeName, interfaces, List.empty) //:+ makeTypeCompanion(i, typeName)
  }

  private def renderComposite(typeName: TypeName, interfaces: Composite, bases: List[Init]): Seq[Defn] = {
    import conv._
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
             override final lazy val definition: ${conv.toSelect(JavaType.get[FinalDefinition])} = {
                ???
             }

             override final def domain: ${conv.toSelect(JavaType.get[IDLDomainCompanion])} = {
              ${conv.toSelectTerm(domainCompanionType)}
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

