package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, EphemeralId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.finaldef.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.finaldef.FinalDefinition._
import com.github.pshirshov.izumi.idealingua.model.finaldef.{FinalDefinition, Service, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.runtime._

import scala.collection.mutable
import scala.meta._


class Translation(typespace: Typespace) {
  protected val conv = new ScalaTypeConverter()
  protected val runtimeTypes = new IDLRuntimeTypes(conv)

  import conv._
  import runtimeTypes._

  final val tDomain = conv.toScala(JavaType(Seq("izumi", "idealingua", "domains"), typespace.domain.id.capitalize))

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
    val index = typespace.all.map(id => id -> conv.toScala(id))

    val types = index.map {
      case (k, v) =>
        q"${conv.toAst(k)} -> classOf[${v.typeFull}]"
    }

    val reverseTypes = index.map {
      case (k, v) =>
        q"classOf[${v.typeFull}] -> ${conv.toAst(k)}"
    }

    toSource(tDomain.javaType.pkg, ModuleId(tDomain.javaType.pkg, typespace.domain.id), Seq(
      q"""object ${tDomain.termName} extends ${tDomainCompanion.init()} {
         def types: Map[${typeId.typeFull}, Class[_]] = Seq(..$types).toMap
         def classes: Map[Class[_], ${typeId.typeFull}] = Seq(..$reverseTypes).toMap
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
        val mt = t.within(m)
        q"""case object ${mt.termName} extends ${t.init()} {
              override def toString: String = ${Lit.String(m)}
            }"""
    }

    Seq(
      q""" sealed trait ${t.typeName} extends $enumElInit {} """
      ,
      q"""object ${t.termName} extends $enumInit {
            type Element = ${t.typeFull}

            override def all: Seq[${t.typeFull}] = Seq(..${members.map(_.name)})

            ..$members

           }"""
    )
  }

  protected def renderAlias(i: Alias): Seq[Defn] = {
    Seq(q"type ${conv.toScala(i.id).typeName} = ${conv.toScala(i.target).typeFull}")
  }

  protected def renderIdentifier(i: Identifier): Seq[Defn] = {
    val fields = typespace.enumFields(i)
    val decls = fields.toScala.all.map {
      f =>
        Term.Param(List.empty, f.name, Some(f.fieldType), None)
    }

    val superClasses = toSuper(fields, List(idtGenerated, tIDLIdentifier.init()))

    // TODO: contradictions

    val typeName = i.id.name

    val interp = Term.Interpolate(Term.Name("s"), List(Lit.String(typeName + "#"), Lit.String("")), List(Term.Name("suffix")))

    val t = conv.toScala(i.id)
    val tools = t.within(s"${i.id.name}Extensions")

    Seq(
      q"""case class ${t.typeName} (..$decls) extends ..$superClasses {
            override def toString: String = {
              val suffix = this.productIterator.map(part => ${tIDLIdentifier.termFull}.escape(part.toString)).mkString(":")
              $interp
            }

         }"""
      ,
      q"""object ${t.termName} extends $typeCompanionInit {
             implicit class ${tools.typeName}(_value: ${t.typeFull}) {
                ${conv.toMethodAst(i.id)}
             }
         }"""
    )
  }

  protected def renderInterface(i: Interface): Seq[Defn] = {
    val fields = typespace.enumFields(i)
    val scalaFieldsEx = fields.toScala
    val scalaFields: Seq[ScalaField] = scalaFieldsEx.all


    // TODO: contradictions
    val decls = scalaFields.toList.map {
      f =>
        Decl.Def(List.empty, f.name, List.empty, List.empty, f.fieldType)
    }

    val scalaIfaces = i.interfaces.map(typespace.apply).toList
    val ifDecls = toSuper(fields, idtGenerated +: scalaIfaces.map {
      iface =>
        conv.toScala(iface.id).init()
    }, "Any")

    val t = conv.toScala(i.id)
    val dtoName = toDtoName(i.id)
    val impl = renderComposite(t.within(dtoName).javaType, i, Seq(i.id), List.empty).toList

    val parents = List(i.id)
    val narrowers = parents.map {
      p =>
        val ifields = typespace.enumFields(typespace(p))

        val constructorCode = ifields.map {
          f =>
            q""" ${Term.Name(f.field.name)} = _value.${Term.Name(f.field.name)}  """
        }

        val tt = toScala(p).within(toDtoName(p))
        q"""def ${Term.Name("to" + p.name.capitalize)}(): ${tt.typeFull} = {
             ${tt.termFull}(..$constructorCode)
            }
          """
    }

    val allParents = typespace.implements(i.id)
    val implementors = typespace.implementingDtos(i.id) ++ typespace.implementingEphemerals(i.id)

    val constructors = implementors.map {
      impl =>
        val (missingInterfaces, allDtoFields) = impl match {
          case i: DTOId =>
            val implementor = typespace(i)
            (
              (implementor.interfaces.toSet -- allParents.toSet).toSeq
              , typespace.enumFields(typespace(i)).toScala
            )
          case i: EphemeralId =>
            val implementor = typespace(i)
            (
              (implementor.toSet -- allParents.toSet).toSeq
              , typespace.enumFields(typespace(i)).toScala
            )
        }

        val constructorCodeThis = fields.map {
          f =>
            q""" ${Term.Name(f.field.name)} = _value.${Term.Name(f.field.name)}  """
        }


        val thisFields = fields.map(_.field).toSet
          .filterNot(f => allDtoFields.nonUnique.exists(_.name.value == f.name))

        val otherFields: Seq[ExtendedField] = missingInterfaces
          .flatMap(mi => typespace.enumFields(typespace(mi)))
          .filterNot(f => thisFields.contains(f.field))
          .filterNot(f => allDtoFields.nonUnique.exists(_.name.value == f.field.name))

        val constructorCodeNonUnique = scalaFieldsEx.nonUnique.map {
          f =>
            q""" ${f.name} = ${f.name}  """
        }

        val constructorCodeOthers = otherFields.map {
          f =>
            q""" ${Term.Name(f.field.name)} = ${idToParaName(f.definedBy)}.${Term.Name(f.field.name)}  """
        }

        val signature = missingInterfaces.toList.map {
          f =>
            Term.Param(List.empty, idToParaName(f), Some(conv.toScala(f).typeFull), None)
        }

        val fullSignature = signature ++ allDtoFields.nonUnique.map {
          f =>
            Term.Param(List.empty, f.name, Some(f.fieldType), None)
        }

        q"""def ${Term.Name("to" + impl.name.capitalize)}(..$fullSignature): ${toScala(impl).typeFull} = {
            ${toScala(impl).termFull}(..${constructorCodeThis ++ constructorCodeOthers ++ constructorCodeNonUnique})
            }
          """
    }

    val allDecls = decls
    val toolDecls = narrowers ++ constructors

    val tools = t.within(s"${i.id.name}Extensions")

    Seq(
      q"""trait ${t.typeName} extends ..$ifDecls {
          ..$allDecls
          }

       """
      ,
      q"""object ${t.termName} extends $typeCompanionInit {
             implicit class ${tools.typeName}(_value: ${t.typeFull}) {
             ${conv.toMethodAst(i.id)}
             ..$toolDecls
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
        // TODO: unify with ephemerals in typespace
        val in = t.within(s"In${method.name.capitalize}")
        val out = t.within(s"Out${method.name.capitalize}")

        val inDef = Interface(InterfaceId(in.javaType.pkg, in.javaType.name), Seq.empty, method.signature.input)
        val outDef = Interface(InterfaceId(out.javaType.pkg, out.javaType.name), Seq.empty, method.signature.input)

        val inputComposite = renderComposite(in.javaType, inDef, method.signature.input, List(serviceInputBase.init()))
        val outputComposite = renderComposite(out.javaType, outDef, method.signature.output, List(serviceOutputBase.init()))

        val inputType = in.typeFull
        val outputType = out.typeFull

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
      q"override def process(input: ${serviceInputBase.typeFull}): ${serviceOutputBase.typeFull} = $forwarder"
    )

    val tAbstractTransport = conv.toScala[AbstractTransport[_]].copy(typeArgs = List(t.typeFull))
    val abstractTransportTpe = tAbstractTransport.init()
    val transportTpe = t.sibling(typeName + "AbstractTransport")
    val tools = t.within(s"${i.id.name}Extensions")

    Seq(
      q"""trait ${t.typeName} extends $idtService {
          import ${t.termBase}._

          override type InputType = ${serviceInputBase.typeFull}
          override type OutputType = ${serviceOutputBase.typeFull}
          override def inputTag: scala.reflect.ClassTag[${serviceInputBase.typeFull}] = scala.reflect.classTag[${serviceInputBase.typeFull}]
          override def outputTag: scala.reflect.ClassTag[${serviceOutputBase.typeFull}] = scala.reflect.classTag[${serviceOutputBase.typeFull}]

          ..${decls.map(_.defn)}
         }"""
      ,
      q"""class ${transportTpe.typeName}
            (
              override val service: ${t.typeFull}
            ) extends $abstractTransportTpe {
            import ${t.termBase}._
            ..$transportDecls
           }"""
      ,
      q"""object ${t.termName} extends $serviceCompanionInit {
            trait ${serviceInputBase.typeName} extends $inputInit {}
            trait ${serviceOutputBase.typeName} extends $outputInit {}

            implicit class ${tools.typeName}(_value: ${t.typeFull}) {
              ${conv.toMethodAst(i.id)}
            }

            ..${decls.flatMap(_.types)}
           }"""
    )
  }

  protected def renderDto(i: DTO): Seq[Defn] = {
    renderComposite(JavaType(i.id), i, i.interfaces, List.empty)
  }

  private def renderComposite(typeName: JavaType, defn: FinalDefinition, interfaces: Composite, bases: List[Init]): Seq[Defn] = {
    val fields = typespace.enumFields(interfaces)
    val scalaIfaces = interfaces.map(typespace.apply).toList
    val scalaFieldsEx = fields.toScala
    val scalaFields: Seq[ScalaField] = scalaFieldsEx.all
    val decls = scalaFields.toList.map {
      f =>
        Term.Param(List.empty, f.name, Some(f.fieldType), None)
    }

    val ifDecls = scalaIfaces.map {
      iface =>
        conv.toScala(iface.id).init()
    }

    // TODO: contradictions

    val superClasses = toSuper(fields, bases ++ ifDecls)

    val t = conv.toScala(typeName)

    val constructorSignature = scalaIfaces.map {
      d =>
        Term.Param(List.empty, definitionToParaName(d), Some(conv.toScala(d.id).typeFull), None)
    } ++ scalaFieldsEx.nonUnique.map {
      f =>
        Term.Param(List.empty, f.name, Some(f.fieldType), None)
    }
    val constructorCode = fields.filterNot(f => scalaFieldsEx.nonUnique.exists(_.name.value == f.field.name)).map {
      f =>
        q""" ${Term.Name(f.field.name)} = ${idToParaName(f.definedBy)}.${Term.Name(f.field.name)}  """
    }

    val constructorCodeNonUnique = scalaFieldsEx.nonUnique.map {
      f =>
        q""" ${f.name} = ${f.name}  """
    }


    val constructors = List(
      q"""def apply(..$constructorSignature): ${t.typeFull} = {
         ${t.termFull}(..${constructorCode ++ constructorCodeNonUnique})
         }"""
    )
    val tools = t.within(s"${typeName.name}Extensions")

    Seq(
      q"""case class ${t.typeName}(..$decls) extends ..$superClasses {

          }
       """
      ,
      q"""object ${t.termName} extends $typeCompanionInit {
              implicit class ${tools.typeName}(_value: ${t.typeFull}) {
              ${conv.toMethodAst(defn.id)}
              }
             ..$constructors
         }"""
    )
  }

  private def toSuper(fields: List[ExtendedField], ifDecls: List[Init]): List[Init] = {
    toSuper(fields, ifDecls, "AnyVal")
  }

  private def toSuper(fields: List[ExtendedField], ifDecls: List[Init], base: String): List[Init] = {
    if (fields.lengthCompare(1) == 0) {
      conv.toScala(JavaType(Seq.empty, base)).init() +: ifDecls
    } else {
      ifDecls
    }
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

