package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.JavaType
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{EnumId, EphemeralId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Service.DefMethod._
import com.github.pshirshov.izumi.idealingua.model.il.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il._
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.runtime.transport.AbstractTransport

import scala.collection.{immutable, mutable}
import scala.meta._


class ScalaTranslator(ts: Typespace, _extensions: Seq[ScalaTranslatorExtension]) {
  protected val context: ScalaTranslationContext = new ScalaTranslationContext(ts)

  protected val extensions: Seq[ScalaTranslatorExtension] = _extensions ++ Seq(ScalaTranslatorMetadataExtension)

  import context._
  import conv._


  protected val packageObjects: mutable.HashMap[ModuleId, mutable.ArrayBuffer[Defn]] = mutable.HashMap[ModuleId, mutable.ArrayBuffer[Defn]]()

  def translate(): Seq[Module] = {
    typespace.domain
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
      typespace.domain.services.flatMap(translateService) ++
      translateDomain()
  }

  protected def translateDomain(): Seq[Module] = {
    val index = typespace.all.map(id => id -> conv.toScala(id)).toList

    val exprs = index.map {
      case (k@EphemeralId(_: EnumId, _), v) =>
        rt.conv.toAst(k) -> q"${v.termBase}.getClass"
      case (k, v) =>
        rt.conv.toAst(k) -> q"classOf[${v.typeFull}]"
    }

    val types = exprs.map({ case (k, v) => q"$k -> $v" })
    val reverseTypes = exprs.map({ case (k, v) => q"$v -> $k" })

    val schema = schemaSerializer.serializeSchema(typespace.domain)

    val references = typespace.domain.referenced.toList.map {
      case (k, v) =>
        q"${conv.toIdConstructor(k)} -> ${conv.toScala(conv.domainCompanionId(v)).termFull}.schema"
    }

    toSource(domainsDomain, ModuleId(domainsDomain.pkg, s"${domainsDomain.name}.scala"), Seq(
      q"""object ${tDomain.termName} extends ${rt.tDomainCompanion.init()} {
         ${conv.toImport}

         lazy val id: ${conv.toScala[DomainId].typeFull} = ${conv.toIdConstructor(typespace.domain.id)}
         lazy val types: Map[${rt.typeId.typeFull}, Class[_]] = Seq(..$types).toMap
         lazy val classes: Map[Class[_], ${rt.typeId.typeFull}] = Seq(..$reverseTypes).toMap
         lazy val referencedDomains: Map[${rt.tDomainId.typeFull}, ${rt.tDomainDefinition.typeFull}] = Seq(..$references).toMap

         protected lazy val serializedSchema: String = ${Lit.String(schema)}
       }"""
    ))

  }


  protected def translateService(definition: Service): Seq[Module] = {
    toSource(Indefinite(definition.id), toModuleId(definition.id), renderService(definition))
  }


  protected def translateDef(definition: ILAst): Seq[Module] = {
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
      case d: Adt =>
        renderAdt(d)
    }

    if (defns.nonEmpty) {
      toSource(Indefinite(definition.id), toModuleId(definition), defns)
    } else {
      Seq.empty
    }
  }

  @deprecated("we should migrate to extensions", "")
  def withInfo[T <: Defn](id: TypeId, defn: T): T = {
    ScalaTranslatorMetadataExtension.withInfo(context, id, defn)
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

        val qqElement = q"""case class ${mt.typeName}(value: ${original.typeFull}) extends ..${List(t.init())}"""
        val qqCompanion = q""" object ${mt.termName} extends ${rt.tAdtElementCompanion.init()} {} """


        val converters = Seq(
          q"""implicit def ${Term.Name("into" + m.name.capitalize)}(value: ${original.typeFull}): ${t.typeFull} = ${mt.termFull}(value) """
          ,
          q"""implicit def ${Term.Name("from" + m.name.capitalize)}(value: ${mt.typeFull}): ${original.typeFull} = value.value"""
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

        mt.termName -> withInfo(i.id,
          q"""case object ${mt.termName} extends ${t.init()} {
              override def toString: String = ${Lit.String(m)}
            }""")
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

    val superClasses = toSuper(fields.fields, List(rt.idtGenerated, rt.tIDLIdentifier.init()))

    // TODO: contradictions

    val typeName = i.id.name

    val interp = Term.Interpolate(Term.Name("s"), List(Lit.String(typeName + "#"), Lit.String("")), List(Term.Name("suffix")))

    val t = conv.toScala(i.id)
    val tools = t.within(s"${i.id.name}Extensions")

    val qqCompanion =
      q"""object ${t.termName} extends ${rt.typeCompanionInit} {
             implicit class ${tools.typeName}(_value: ${t.typeFull}) {
                ${rt.conv.toMethodAst(i.id)}
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

  private def withExtensions[I <: TypeId, D <: Defn, C <: Defn]
  (
    id: I
    , entity: D
    , companion: C
    , entityTransformer: ScalaTranslatorExtension => (ScalaTranslationContext, I, D) => D
    , companionTransformer: ScalaTranslatorExtension => (ScalaTranslationContext, I, C) => C
  ) = {
    val extendedEntity = extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(context, id, acc)
    }
    val extendedCompanion = extensions.foldLeft(companion) {
      case (acc, v) =>
        companionTransformer(v)(context, id, acc)
    }

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

    val scalaIfaces = i.interfaces
    val ifDecls = toSuper(fields.fields, rt.idtGenerated +: scalaIfaces.map {
      iface =>
        conv.toScala(iface).init()
    }, "Any")

    val t = conv.toScala(i.id)
    val eid = EphemeralId(i.id, typespace.toDtoName(i.id))
    val impl = renderComposite(eid, List.empty).toList

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
             ${rt.conv.toMethodAst(i.id)}
             ..$toolDecls
             }

             ..$impl
         }"""

    withExtensions(i.id, qqInterface, qqInterfaceCompanion, _.handleInterface, _.handleInterfaceCompanion)
  }


  protected def renderService(i: Service): Seq[Defn] = {
    val typeName = i.id.name

    case class ServiceMethodProduct(defn: Stat, routingClause: Case, types: Seq[Defn])

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

        val inputComposite = renderComposite(inDef, List(serviceInputBase.init()))
        val outputComposite = renderComposite(outDef, List(serviceOutputBase.init()))

        val inputType = in.typeFull
        val outputType = out.typeFull

        ServiceMethodProduct(
          q"def ${Term.Name(method.name)}(input: $inputType): Result[$outputType]"
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
      q"override def process(input: ${serviceInputBase.typeFull}): service.Result[${serviceOutputBase.typeFull}] = $forwarder"
    )

    val tAbstractTransport = conv.toScala[AbstractTransport[_]].parameterize(List(Type.Name("S")))
    val abstractTransportTpe = tAbstractTransport.init()
    val transportTpe = t.sibling(typeName + "AbstractTransport")
    val tools = t.within(s"${i.id.name}Extensions")

    Seq(
      withInfo(i.id,
        q"""trait ${t.typeName} extends ${rt.idtService} {
          import ${t.termBase}._

          override type InputType = ${serviceInputBase.typeFull}
          override type OutputType = ${serviceOutputBase.typeFull}
          override def inputClass: Class[${serviceInputBase.typeFull}] = classOf[${serviceInputBase.typeFull}]
          override def outputClass: Class[${serviceOutputBase.typeFull}] = classOf[${serviceOutputBase.typeFull}]

          ..${decls.map(_.defn)}
         }""")
      ,
      q"""class ${transportTpe.typeName}[S <: ${t.typeFull}]
            (
              override val service: S
            ) extends $abstractTransportTpe {
            import ${t.termBase}._
            ..$transportDecls
           }"""
      ,
      q"""object ${t.termName} extends ${rt.serviceCompanionInit} {
            sealed trait ${serviceInputBase.typeName} extends Any with ${rt.inputInit} {}
            sealed trait ${serviceOutputBase.typeName} extends Any with ${rt.outputInit} {}

            implicit class ${tools.typeName}(_value: ${t.typeFull}) {
              ${rt.conv.toMethodAst(i.id)}
            }

            ..${decls.flatMap(_.types)}
           }"""
    )
  }

  protected def renderDto(i: DTO): Seq[Defn] = {
    renderComposite(i.id, List.empty)
  }

  private def renderComposite(id: TypeId, bases: List[Init]): Seq[Defn] = {
    val interfaces = typespace.getComposite(id)
    val fields = typespace.enumFields(interfaces).toScala
    val decls = fields.all.toParams

    val ifDecls = interfaces.map {
      iface =>
        conv.toScala(iface).init()
    }

    val embedded = fields.fields.all
      .map(_.definedBy)
      .collect({ case i: InterfaceId => i })
      .filterNot(interfaces.contains)
    // TODO: contradictions

    val superClasses = toSuper(fields.fields, bases ++ ifDecls)

    val t = conv.toScala(id)

    val interfaceParams = (interfaces ++ embedded)
      .distinct
      .map {
        d =>
          (idToParaName(d), conv.toScala(d).typeFull)
      }.toParams

    val fieldParams = fields.nonUnique.toParams

    val constructorSignature = interfaceParams ++ fieldParams

    val constructorCode = fields.fields.all.filterNot(f => fields.nonUnique.exists(_.name.value == f.field.name)).map {
      f =>
        q""" ${Term.Name(f.field.name)} = ${idToParaName(f.definedBy)}.${Term.Name(f.field.name)}  """
    }

    val constructorCodeNonUnique = fields.nonUnique.map {
      f =>
        q""" ${f.name} = ${f.name}  """
    }


    val constructors = List(
      q"""def apply(..$constructorSignature): ${t.typeFull} = {
         ${t.termFull}(..${constructorCode ++ constructorCodeNonUnique})
         }"""
    )
    val tools = t.within(s"${id.name.capitalize}Extensions")

    val converters: List[Defn.Def] = mkConverters(id, fields)

    val qqComposite = q"""case class ${t.typeName}(..$decls) extends ..$superClasses {}"""

    val qqCompositeCompanion =
      q"""object ${t.termName} extends ${rt.typeCompanionInit} {
              implicit class ${tools.typeName}(_value: ${t.typeFull}) {
                ${rt.conv.toMethodAst(id)}

                ..$converters
              }
             ..$constructors
         }"""

    withExtensions(id, qqComposite, qqCompositeCompanion, _.handleComposite, _.handleCompositeCompanion)
  }

  private def mkConverters(id: TypeId, fields: ScalaFields) = {
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

  implicit class ScalaFieldsExt(fields: TraversableOnce[ScalaField]) {
    def toParams: List[Term.Param] = fields.map(f => (f.name, f.fieldType)).toParams
  }

  implicit class NamedTypeExt(fields: TraversableOnce[(Term.Name, Type)]) {
    def toParams: List[Term.Param] = fields.map(f => (f._1, f._2)).map(toParam).toList
  }


  protected def toParam(p: (Term.Name, Type)): Term.Param = {
    Term.Param(List.empty, p._1, Some(p._2), None)
  }

  private def toSuper(fields: Fields, ifDecls: List[Init]): List[Init] = {
    toSuper(fields, ifDecls, "AnyVal")
  }

  private def toSuper(fields: Fields, ifDecls: List[Init], base: String): List[Init] = {
    if (fields.size == 0) {
      conv.toScala(JavaType(Seq.empty, base)).init() +: ifDecls
    } else {
      ifDecls
    }
  }

  //private def definitionToParaName(d: FinalDefinition) = idToParaName(d.id)

  private def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)

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

  private def toSource(id: Indefinite, moduleId: ModuleId, traitDef: Seq[Defn]) = {
    val code = traitDef.map(_.toString()).mkString("\n\n")
    val content: String = withPackage(id.pkg, code)
    Seq(Module(moduleId, content))
  }

  private def withPackage(pkg: idealingua.model.common.Package, code: String) = {
    val content = if (pkg.isEmpty) {
      code
    } else {
      s"""package ${pkg.mkString(".")}
         |
         |import _root_.${rt.basePkg}._
         |
         |$code
       """.stripMargin
    }
    content
  }


}


