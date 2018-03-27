package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.JavaType
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.EphemeralId
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Service.DefMethod._
import com.github.pshirshov.izumi.idealingua.model.il.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il._
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}

import scala.collection.immutable
import scala.meta._


class ScalaTranslator(ts: Typespace, _extensions: Seq[ScalaTranslatorExtension]) {
  protected val context: ScalaTranslationContext = new ScalaTranslationContext(ts)

  protected val extensions: Seq[ScalaTranslatorExtension] = _extensions //++ Seq(ScalaTranslatorMetadataExtension)

  import ScalaField._
  import context._
  import conv._


  def translate(): Seq[Module] = {
    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val aliases = typespace.domain.types
      .collect {
        case a: Alias =>
          toModuleId(a) -> renderAlias(a)
      }
      .toMultimap
      .mapValues(_.flatten.toSeq)


    val packageObjects = aliases.map {
      case (id, content) =>
        val pkgName = id.name.split('.').head

        val code =
          s"""
             |package object $pkgName {
             |${content.map(_.toString()).mkString("\n\n")}
             |}
           """.stripMargin
        Module(id, context.modules.withPackage(id.path.init, code))
    }

    val modules = Seq(
      typespace.domain.types.flatMap(translateDef)
      , typespace.domain.services.flatMap(translateService)
      , packageObjects
    ).flatten

    extensions.foldLeft(modules) {
      case (acc, ext) =>
        ext.handleModules(context, acc)
    }
  }


  protected def translateService(definition: Service): Seq[Module] = {
    context.modules.toSource(Indefinite(definition.id), toModuleId(definition.id), renderService(definition))
  }

  protected def translateDef(definition: ILAst): Seq[Module] = {
    val defns = definition match {
      case i: Enumeration =>
        renderEnumeration(i)
      case i: Identifier =>
        renderIdentifier(i)
      case i: Interface =>
        renderInterface(i)
      case d: DTO =>
        renderDto(d)
      case d: Adt =>
        renderAdt(d)
      case _: Alias =>
        Seq()
    }

    context.modules.toSource(Indefinite(definition.id), toModuleId(definition), defns)
  }

  def renderAdt(i: Adt): Seq[Defn] = {
    val t = conv.toScala(i.id)

    val duplicates = i.alternatives.groupBy(v => v).filter(_._2.lengthCompare(1) > 0)
    if (duplicates.nonEmpty) {
      throw new IDLException(s"Duplicated adt elements: $duplicates")
    }

    val members = i.alternatives.flatMap {
      m =>
        val mt = t.within(m.name)
        val original = conv.toScala(m)

        val qqElement = q"""case class ${mt.typeName}(value: ${original.typeAbsolute}) extends ..${List(t.init())}"""
        val qqCompanion = q""" object ${mt.termName} extends ${rt.tAdtElementCompanion.init()} {} """


        val converters = Seq(
          q"""implicit def ${Term.Name("into" + m.name.capitalize)}(value: ${original.typeAbsolute}): ${t.typeFull} = ${mt.termFull}(value) """
          ,
          q"""implicit def ${Term.Name("from" + m.name.capitalize)}(value: ${mt.typeFull}): ${original.typeAbsolute} = value.value"""
        )

        val id = EphemeralId(i.id, m.name)
        withExtensions(id, qqElement, qqCompanion, _.handleAdtElement, _.handleAdtElementCompanion) ++ converters
    }

    val qqAdt = q""" sealed trait ${t.typeName} extends ${rt.adtElInit}{} """
    val qqAdtCompanion =
      q"""object ${t.termName} extends ${rt.adtInit} {
            import scala.language.implicitConversions

            type Element = ${t.typeFull}

            ..$members
           }"""
    withExtensions(i.id, qqAdt, qqAdtCompanion, _.handleAdt, _.handleAdtCompanion)
  }

  def renderEnumeration(i: Enumeration): Seq[Defn] = {
    val t = conv.toScala(i.id)

    val duplicates = i.members.groupBy(v => v).filter(_._2.lengthCompare(1) > 0)
    if (duplicates.nonEmpty) {
      throw new IDLException(s"Duplicated enum elements: $duplicates")
    }

    val members = i.members.map {
      m =>
        val mt = t.within(m)
        val element =
          q"""case object ${mt.termName} extends ${t.init()} {
              override def toString: String = ${Lit.String(m)}
            }"""

        mt.termName -> withExtensions(i.id, element, _.handleEnumElement)
    }

    val parseMembers = members.map {
      case (termName, _) =>
        val termString = termName.value
        p"""case ${Lit.String(termString)} => $termName"""
    }

    val qqEnum = q""" sealed trait ${t.typeName} extends ${rt.enumElInit} {} """
    val qqEnumCompanion =
      q"""object ${t.termName} extends ${rt.enumInit} {
            type Element = ${t.typeFull}

            override def all: Seq[${t.typeFull}] = Seq(..${members.map(_._1)})

            override def parse(value: String) = value match {
              ..case $parseMembers
            }

            ..${members.map(_._2)}
           }"""

    withExtensions(i.id, qqEnum, qqEnumCompanion, _.handleEnum, _.handleEnumCompanion)
  }

  protected def renderAlias(i: Alias): Seq[Defn] = {
    Seq(q"type ${conv.toScala(i.id).typeName} = ${conv.toScala(i.target).typeFull}")
  }

  protected def renderIdentifier(i: Identifier): Seq[Defn] = {
    val fields = typespace.enumFields(i).toScala
    val decls = fields.all.toParams

    val superClasses = withAnyval(fields.fields, List(rt.idtGenerated, rt.tIDLIdentifier.init()))

    // TODO: contradictions

    val typeName = i.id.name

    val interp = Term.Interpolate(Term.Name("s"), List(Lit.String(typeName + "#"), Lit.String("")), List(Term.Name("suffix")))

    val t = conv.toScala(i.id)
    val tools = t.within(s"${i.id.name}Extensions")

    val qqCompanion =
      q"""object ${t.termName} extends ${rt.typeCompanionInit} {
             implicit class ${tools.typeName}(_value: ${t.typeFull}) {
                ${rt.modelConv.toMethodAst(i.id)}
             }
         }"""


    val qqIdentifier =
      q"""case class ${t.typeName} (..$decls) extends ..$superClasses {
            override def toString: String = {
              val suffix = this.productIterator.map(part => ${rt.tIDLIdentifier.termFull}.escape(part.toString)).mkString(":")
              $interp
            }
         }"""


    withExtensions(i.id, qqIdentifier, qqCompanion, _.handleIdentifier, _.handleIdentifierCompanion)
  }

  private def withExtensions[I <: TypeId, D <: Defn](id: I
                                                     , entity: D
                                                     , entityTransformer: ScalaTranslatorExtension => (ScalaTranslationContext, I, D) => D
                                                    ): D = {
    extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(context, id, acc)
    }
  }

  protected[toscala] def withExtensions[I <: TypeId, D <: Defn, C <: Defn]
  (
    id: I
    , entity: D
    , companion: C
    , entityTransformer: ScalaTranslatorExtension => (ScalaTranslationContext, I, D) => D
    , companionTransformer: ScalaTranslatorExtension => (ScalaTranslationContext, I, C) => C
  ): Seq[Defn] = {
    val extendedEntity = withExtensions(id, entity, entityTransformer)
    val extendedCompanion = withExtensions(id, companion, companionTransformer)
    Seq(
      extendedEntity
      , extendedCompanion
    )
  }

  protected def renderInterface(i: Interface): Seq[Defn] = {
    val fields = typespace.enumFields(i).toScala

    // TODO: contradictions
    val decls = fields.all.map {
      f =>
        Decl.Def(List.empty, f.name, List.empty, List.empty, f.fieldType)
    }

    val ifDecls = {
      val scalaIfaces = rt.idtGenerated +: i.interfaces.map(conv.toScala).map(_.init())
      withAny(fields.fields, scalaIfaces)
    }

    val t = conv.toScala(i.id)
    val eid = EphemeralId(i.id, typespace.toDtoName(i.id))

    val implStructure = mkStructure(eid)
    val impl = implStructure.defns(List.empty).toList

    val parents = List(i.id) ++ i.concepts
    val narrowers = parents.distinct.map {
      p =>
        val ifields = typespace.enumFields(typespace(p))

        val constructorCode = ifields.all.map {
          f =>
            q""" ${Term.Name(f.field.name)} = _value.${Term.Name(f.field.name)}  """
        }

        val tt = toScala(p).within(typespace.toDtoName(p))
        q"""def ${Term.Name("as" + p.name.capitalize)}(): ${tt.typeFull} = {
             ${tt.termFull}(..$constructorCode)
            }
          """
    }

    val constructors = typespace.compatibleImplementors(i.id).map {
      t =>
        val requiredParameters = t.requiredParameters
        val fieldsToCopyFromInterface: Set[Field] = t.fieldsToCopyFromInterface
        val fieldsToTakeFromParameters: Set[ExtendedField] = t.fieldsToTakeFromParameters

        val constructorCodeThis = fieldsToCopyFromInterface.toList.map {
          f =>
            q""" ${Term.Name(f.name)} = _value.${Term.Name(f.name)}  """
        }

        val constructorCodeOthers = fieldsToTakeFromParameters.map {
          f =>
            q""" ${Term.Name(f.field.name)} = ${idToParaName(f.definedBy)}.${Term.Name(f.field.name)}  """
        }

        val signature = requiredParameters.map(f => (idToParaName(f), conv.toScala(f).typeFull)).toParams

        val nonUniqueFields: immutable.Seq[ScalaField] = t.fields.toScala.nonUnique

        val fullSignature = signature ++ nonUniqueFields.toParams

        val constructorCodeNonUnique = nonUniqueFields.map {
          f =>
            q""" ${f.name} = ${f.name}  """
        }

        val impl = t.typeToConstruct
        q"""def ${Term.Name("to" + impl.name.capitalize)}(..$fullSignature): ${toScala(impl).typeFull} = {
            ${toScala(impl).termFull}(..${constructorCodeThis ++ constructorCodeOthers ++ constructorCodeNonUnique})
            }
          """
    }

    val allDecls = decls
    val converters = mkConverters(i.id, fields)

    val toolDecls = narrowers ++ constructors ++ converters

    val tools = t.within(s"${i.id.name}Extensions")

    val qqInterface =
      q"""trait ${t.typeName} extends ..$ifDecls {
          ..$allDecls
          }

       """

    val qqInterfaceCompanion =
      q"""object ${t.termName} extends ${rt.typeCompanionInit} {
             implicit class ${tools.typeName}(_value: ${t.typeFull}) {
             ${rt.modelConv.toMethodAst(i.id)}
             ..$toolDecls
             }

             def apply(..${implStructure.decls}) = ${conv.toScala(eid).termName}(..${implStructure.names})
             ..$impl
         }"""

    withExtensions(i.id, qqInterface, qqInterfaceCompanion, _.handleInterface, _.handleInterfaceCompanion)
  }


  protected def renderService(i: Service): Seq[Defn] = {
    val typeName = i.id.name

    val t = conv.toScala(i.id)

    val serviceInputBase = t.within(s"In${typeName.capitalize}")
    val serviceOutputBase = t.within(s"Out${typeName.capitalize}")

    val decls = i.methods.map {
      case method: RPCMethod =>
        // TODO: unify with ephemerals in typespace
        val in = t.within(s"In${method.name.capitalize}")
        val out = t.within(s"Out${method.name.capitalize}")

        val inDef = EphemeralId(i.id, in.fullJavaType.name)
        val outDef = EphemeralId(i.id, out.fullJavaType.name)

        val inputComposite = mkStructure(inDef)
        val outputComposite = mkStructure(outDef)

        val inputType = in.typeFull
        val outputType = out.typeFull


        val defns = Seq(
          inputComposite.defns(List(serviceInputBase.init()))
          , outputComposite.defns(List(serviceOutputBase.init()))
        ).flatten

        ServiceMethodProduct(
          method.name
          , inputComposite
          , outputComposite
          , inputType
          , outputType
          , defns
        )
    }


    val tools = t.within(s"${i.id.name}Extensions")

    val fullService = t.parameterize("R")
    val fullServiceType = fullService.typeFull
    val qqService =
      q"""trait ${t.typeName}[R[_]] extends ${rt.idtService.parameterize("R").init()} {
          import ${t.termBase}._

          override type InputType = ${serviceInputBase.typeFull}
          override type OutputType = ${serviceOutputBase.typeFull}
          override def inputClass: Class[${serviceInputBase.typeFull}] = classOf[${serviceInputBase.typeFull}]
          override def outputClass: Class[${serviceOutputBase.typeFull}] = classOf[${serviceOutputBase.typeFull}]

          ..${decls.map(_.defn)}
         }"""
    val qqServiceCompanion =
      q"""object ${t.termName} extends ${rt.serviceCompanionInit} {
            sealed trait ${serviceInputBase.typeName} extends Any with ${rt.inputInit} {}
            sealed trait ${serviceOutputBase.typeName} extends Any with ${rt.outputInit} {}

            implicit class ${tools.typeName}[R[_]](_value: $fullServiceType) {
              ${rt.modelConv.toMethodAst(i.id)}
            }

            ..${decls.flatMap(_.types)}
           }"""


    val dispatchers = {
      val dServer = {
        val forwarder = Term.Match(Term.Name("input"), decls.map(_.routingClause))
        val transportDecls =
          List(
            q"override def dispatch(input: ${serviceInputBase.typeFull}): R[${serviceOutputBase.typeFull}] = $forwarder"
          )
        val dispatcherInTpe = rt.serverDispatcher.parameterize("R", "S").init()
        val dispactherTpe = t.sibling(typeName + "ServerDispatcher")
        q"""class ${dispactherTpe.typeName}[R[+_], S <: $fullServiceType]
            (
              val service: S
            ) extends $dispatcherInTpe with ${rt.idtGenerated} {
            import ${t.termBase}._
            ..$transportDecls
           }"""
      }


      val dClient = {
        val dispatcherInTpe = rt.clientDispatcher.parameterize("R", "S")
        val dispactherTpe = t.sibling(typeName + "ClientDispatcher")

        val transportDecls = decls.map(_.defnDispatch)

        q"""class ${dispactherTpe.typeName}[R[+_], S <: $fullServiceType]
            (
              dispatcher: ${dispatcherInTpe.typeFull}
            ) extends ${fullService.init()} with ${rt.idtGenerated} {
            import ${t.termBase}._
           ..$transportDecls
           }"""
      }

      val dCompr = {
        val dispatcherInTpe = rt.clientWrapper.parameterize("R", "S")

        val forwarder = decls.map(_.defnCompress)

        val dispactherTpe = t.sibling(typeName + "ClientWrapper")
        q"""class ${dispactherTpe.typeName}[R[+_], S <: $fullServiceType]
            (
              val service: S
            ) extends ${dispatcherInTpe.init()} with ${rt.idtGenerated} {
            import ${t.termBase}._

            ..$forwarder
           }"""
      }

      val dExpl = {
        val explodedService = t.sibling(typeName + "Unwrapped")
        val explodedDecls = decls.flatMap(in => Seq(in.defnExploded))


        val dispactherTpe = t.sibling(typeName + "ServerWrapper")

        val transportDecls = decls.flatMap(in => Seq(in.defnExplode))

        val wrapperTpe = rt.serverWrapper.parameterize("R", "S")
        val wrapped = explodedService.parameterize("R", "S")

        Seq(
          q"""trait ${explodedService.typeName}[R[+_], S <: $fullServiceType]
              extends ${wrapperTpe.init()} with ${rt.idtGenerated} {
            import ${t.termBase}._
           ..$explodedDecls
           }"""
          ,
          q"""class ${dispactherTpe.typeName}[R[+_], S <: $fullServiceType](val service: ${wrapped.typeFull})
              extends ${fullService.init()} with ${rt.idtGenerated} {
            import ${t.termBase}._
           ..$transportDecls
           }"""
        )
      }

      Seq(dServer, dClient, dCompr) ++ dExpl
    }


    dispatchers ++ withExtensions(i.id, qqService, qqServiceCompanion, _.handleService, _.handleServiceCompanion)
  }

  protected def renderDto(i: DTO): Seq[Defn] = {
    renderComposite(i.id, List.empty)
  }


  private def renderComposite(id: TypeId, bases: List[Init]): Seq[Defn] = {
    mkStructure(id).defns(bases)
  }

  private def mkStructure(id: TypeId) = {
    val interfaces = typespace.getComposite(id)
    val fields = typespace.enumFields(interfaces).toScala
    new CompositeStructure(this, context, conv, id, fields, interfaces)
  }

  protected[toscala] def mkConverters(id: TypeId, fields: ScalaFields): List[Defn.Def] = {
    val converters = typespace.sameSignature(id).map {
      same =>
        val code = fields.all.map {
          f =>
            q""" ${f.name} = _value.${f.name}  """
        }
        q"""def ${Term.Name("into" + same.id.name.capitalize)}(): ${toScala(same.id).typeFull} = {
              ${toScala(same.id).termFull}(..$code)
            }
          """

    }
    converters
  }


  protected[toscala] def withAnyval(fields: Fields, ifDecls: List[Init]): List[Init] = {
    addAnyBase(fields, ifDecls, "AnyVal")
  }

  private def withAny(fields: Fields, ifDecls: List[Init]): List[Init] = {
    addAnyBase(fields, ifDecls, "Any")
  }


  private def canBeAnyValField(typeId: TypeId): Boolean = {
    typeId match {
      case _: Builtin =>
        true

      case t =>
        val fields = typespace.enumFields(typespace.apply(t))
        (fields.size > 1) || (fields.size == 1 && !fields.all.exists(v => canBeAnyValField(v.field.typeId)))
    }
  }

  private def addAnyBase(fields: Fields, ifDecls: List[Init], base: String): List[Init] = {
    if (fields.size == 1 && fields.all.forall(f => canBeAnyValField(f.field.typeId))) {
      conv.toScala(JavaType(Seq.empty, base)).init() +: ifDecls
    } else {
      ifDecls
    }
  }

  //private def definitionToParaName(d: FinalDefinition) = idToParaName(d.id)

  protected[toscala] def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)

  private def toModuleId(defn: ILAst): ModuleId = {
    defn match {
      case i: Alias =>
        val concrete = i.id
        ModuleId(concrete.pkg, s"${concrete.pkg.last}.scala")

      case other =>
        val id = other.id
        toModuleId(id)
    }
  }

  private def toModuleId(id: TypeId): ModuleId = {
    ModuleId(id.pkg, s"${id.name}.scala")
  }


}



