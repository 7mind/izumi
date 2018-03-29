package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.DTOId
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst.Service.DefMethod._
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il._
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{ScalaField, ServiceMethodProduct}

import scala.meta._


class ScalaTranslator(ts: Typespace, extensions: Seq[ScalaTranslatorExtension]) {
  protected val ctx: ScalaTranslationContext = new ScalaTranslationContext(ts, extensions)

  import ScalaField._
  import ctx._
  import conv._


  def translate(): Seq[Module] = {
    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val aliases = typespace.domain.types
      .collect {
        case a: Alias =>
          ctx.modules.toModuleId(a) -> renderAlias(a)
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
        Module(id, ctx.modules.withPackage(id.path.init, code))
    }

    val modules = Seq(
      typespace.domain.types.flatMap(translateDef)
      , typespace.domain.services.flatMap(translateService)
      , packageObjects
    ).flatten

    ext.extend(modules)
  }


  protected def translateService(definition: Service): Seq[Module] = {
    ctx.modules.toSource(IndefiniteId(definition.id), ctx.modules.toModuleId(definition.id), renderService(definition))
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

    ctx.modules.toSource(IndefiniteId(definition.id), ctx.modules.toModuleId(definition), defns)
  }

  protected def renderDto(i: DTO): Seq[Defn] = {
    ctx.tools.mkStructure(i.id).defns(List.empty)
  }

  protected def renderAlias(i: Alias): Seq[Defn] = {
    Seq(q"type ${conv.toScala(i.id).typeName} = ${conv.toScala(i.target).typeFull}")
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
        val qqCompanion = q""" object ${mt.termName} {} """


        val converters = Seq(
          q"""implicit def ${Term.Name("into" + m.name.capitalize)}(value: ${original.typeAbsolute}): ${t.typeFull} = ${mt.termFull}(value) """
          ,
          q"""implicit def ${Term.Name("from" + m.name.capitalize)}(value: ${mt.typeFull}): ${original.typeAbsolute} = value.value"""
        )

        val id = DTOId(i.id, m.name)
        ext.extend(id, qqElement, qqCompanion, _.handleAdtElement, _.handleAdtElementCompanion) ++ converters
    }

    val qqAdt = q""" sealed trait ${t.typeName} extends ${rt.adtEl.init()}{} """
    val qqAdtCompanion =
      q"""object ${t.termName} extends ${rt.adt.init()} {
            import scala.language.implicitConversions

            type Element = ${t.typeFull}

            ..$members
           }"""
    ext.extend(i.id, qqAdt, qqAdtCompanion, _.handleAdt, _.handleAdtCompanion)
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

        mt.termName -> ext.extend(i.id, element, _.handleEnumElement)
    }

    val parseMembers = members.map {
      case (termName, _) =>
        val termString = termName.value
        p"""case ${Lit.String(termString)} => $termName"""
    }

    val qqEnum = q""" sealed trait ${t.typeName} extends ${rt.enumEl.init()} {} """
    val qqEnumCompanion =
      q"""object ${t.termName} extends ${rt.enum.init()} {
            type Element = ${t.typeFull}

            override def all: Seq[${t.typeFull}] = Seq(..${members.map(_._1)})

            override def parse(value: String) = value match {
              ..case $parseMembers
            }

            ..${members.map(_._2)}
           }"""

    ext.extend(i.id, qqEnum, qqEnumCompanion, _.handleEnum, _.handleEnumCompanion)
  }


  protected def renderIdentifier(i: Identifier): Seq[Defn] = {
    val fields = typespace.structure(i).toScala
    val decls = fields.all.toParams

    val superClasses = ctx.tools.withAnyval(fields.fields, List(rt.generated.init(), rt.tIDLIdentifier.init()))

    // TODO: contradictions

    val typeName = i.id.name

    val interp = Term.Interpolate(Term.Name("s"), List(Lit.String(typeName + "#"), Lit.String("")), List(Term.Name("suffix")))

    val t = conv.toScala(i.id)
    val tools = t.within(s"${i.id.name}Extensions")

    val qqTools = q"""implicit class ${tools.typeName}(_value: ${t.typeFull}) { }"""
    val extended = ext.extend(i.id, qqTools, _.handleIdentifierTools)

    val qqCompanion = q"""object ${t.termName} { $extended}"""

    val qqIdentifier =
      q"""case class ${t.typeName} (..$decls) extends ..$superClasses {
            override def toString: String = {
              val suffix = this.productIterator.map(part => ${rt.tIDLIdentifier.termFull}.escape(part.toString)).mkString(":")
              $interp
            }
         }"""


    ext.extend(i.id, qqIdentifier, qqCompanion, _.handleIdentifier, _.handleIdentifierCompanion)
  }

  protected def renderInterface(i: Interface): Seq[Defn] = {
    val fields = typespace.structure(i).toScala

    // TODO: contradictions
    val decls = fields.all.map {
      f =>
        Decl.Def(List.empty, f.name, List.empty, List.empty, f.fieldType)
    }

    val ifDecls = {
      val scalaIfaces = (rt.generated +: i.superclasses.interfaces.map(conv.toScala)).map(_.init())
      ctx.tools.withAny(fields.fields, scalaIfaces)
    }

    val t = conv.toScala(i.id)
    val eid = DTOId(i.id, typespace.toDtoName(i.id))

    val implStructure = ctx.tools.mkStructure(eid)
    val impl = implStructure.defns(List.empty).toList

    val structuralParents = List(i.id) ++ i.superclasses.concepts // we don't add explicit parents here because their converters are available
    val narrowers = structuralParents.distinct.map {
      p =>
        val ifields = typespace.enumFields(p)

        val constructorCode = ifields.all.map {
          f =>
            q""" ${Term.Name(f.field.name)} = _value.${Term.Name(f.field.name)}  """
        }

        val parentType = toScala(p)
        val tt = parentType.within(typespace.toDtoName(p))
        q"""def ${Term.Name("as" + p.name.capitalize)}(): ${parentType.typeFull} = {
             ${tt.termFull}(..$constructorCode)
            }
          """
    }

    val constructors = typespace.compatibleImplementors(i.id).map {
      t =>
        val instanceFields = t.parentInstanceFields
        val childMixinFields = t.mixinsInstancesFields
        val localFields = t.localFields

        val constructorSignature = Seq(
          childMixinFields.map(_.definedBy).map(f => (ctx.tools.idToParaName(f), conv.toScala(f).typeFull))
          , localFields.map(f => (Term.Name(f.name), conv.toScala(f.typeId).typeFull))
        ).flatten.toParams

        val constructorCodeThis = instanceFields.toList.map {
          f =>
            q""" ${Term.Name(f.name)} = _value.${Term.Name(f.name)}  """
        }

        val constructorCodeOthers = childMixinFields.map {
          f =>
            q""" ${Term.Name(f.field.name)} = ${ctx.tools.idToParaName(f.definedBy)}.${Term.Name(f.field.name)}  """
        }

        val constructorCodeNonUnique = localFields.map {
          f =>
            val term = Term.Name(f.name)
            q""" $term = $term """
        }

        val impl = t.typeToConstruct

        q"""def ${Term.Name("to" + impl.name.capitalize)}(..$constructorSignature): ${toScala(impl).typeFull} = {
            ${toScala(impl).termFull}(..${constructorCodeThis ++ constructorCodeOthers ++ constructorCodeNonUnique})
            }
          """
    }


    val extensions = {
      val converters = ctx.tools.mkConverters(i.id, fields)

      val toolDecls = narrowers ++ constructors ++ converters

      val tools = t.within(s"${i.id.name}Extensions")

      val qqTools = q"""implicit class ${tools.typeName}(_value: ${t.typeFull}) { ..$toolDecls }"""

      ext.extend(i.id, qqTools, _.handleInterfaceTools)
    }

    val qqInterface =
      q"""trait ${t.typeName} extends ..$ifDecls {
          ..$decls
          }

       """

    val qqInterfaceCompanion =
      q"""object ${t.termName} {
             def apply(..${implStructure.decls}) = ${conv.toScala(eid).termName}(..${implStructure.names})

             $extensions

             ..$impl
         }"""

    ext.extend(i.id, qqInterface, qqInterfaceCompanion, _.handleInterface, _.handleInterfaceCompanion)
  }


  protected def renderService(i: Service): Seq[Defn] = {
    val typeName = i.id.name

    val t = conv.toScala(IndefiniteId(i.id))

    val serviceInputBase = t.within(s"In${typeName.capitalize}")
    val serviceOutputBase = t.within(s"Out${typeName.capitalize}")

    val decls = i.methods.map {
      case method: RPCMethod =>
        // TODO: unify with ephemerals in typespace
        val in = t.within(s"In${method.name.capitalize}")
        val out = t.within(s"Out${method.name.capitalize}")

        val inDef = DTOId(i.id, in.fullJavaType.name)
        val outDef = DTOId(i.id, out.fullJavaType.name)

        val inputComposite = ctx.tools.mkStructure(inDef)
        val outputComposite = ctx.tools.mkStructure(outDef)

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
    val qqTools = q"""implicit class ${tools.typeName}[R[_]](_value: $fullServiceType) {}"""
    val extTools = ext.extend(i.id, qqTools, _.handleServiceTools)

    val qqServiceCompanion =
      q"""object ${t.termName} {
            sealed trait ${serviceInputBase.typeName} extends Any with ${rt.input.init()} {}
            sealed trait ${serviceOutputBase.typeName} extends Any with ${rt.output.init()} {}

            $extTools

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
            ) extends $dispatcherInTpe with ${rt.generated.init()} {
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
            ) extends ${fullService.init()} with ${rt.generated.init()} {
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
            ) extends ${dispatcherInTpe.init()} with ${rt.generated.init()} {
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
              extends ${wrapperTpe.init()} with ${rt.generated.init()} {
            import ${t.termBase}._
           ..$explodedDecls
           }"""
          ,
          q"""class ${dispactherTpe.typeName}[R[+_], S <: $fullServiceType](val service: ${wrapped.typeFull})
              extends ${fullService.init()} with ${rt.generated.init()} {
            import ${t.termBase}._
           ..$transportDecls
           }"""
        )
      }

      Seq(dServer, dClient, dCompr) ++ dExpl
    }


    dispatchers ++ ext.extend(i.id, qqService, qqServiceCompanion, _.handleService, _.handleServiceCompanion)
  }


}
