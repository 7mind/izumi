package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.DTOId
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Service, TypeDef}
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions._
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.{AdtElementProduct, AdtProduct, EnumProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.{CogenProduct, RenderableCogenProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{CompositeStructure, ScalaField, ServiceMethodProduct}

import scala.meta._

object ScalaTranslator {
  final val defaultExtensions = Seq(
    ConvertersExtension
    , IfaceConstructorsExtension
    , IfaceNarrowersExtension
    , AnyvalExtension
  )
}

class ScalaTranslator(ts: Typespace, extensions: Seq[ScalaTranslatorExtension]) {
  protected val ctx: STContext = new STContext(ts, extensions)

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

  protected def translateDef(definition: TypeDef): Seq[Module] = {
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
        RenderableCogenProduct.empty
    }

    ctx.modules.toSource(IndefiniteId(definition.id), ctx.modules.toModuleId(definition), defns)
  }

  protected def renderDto(i: DTO): RenderableCogenProduct = {
    defns(ctx.tools.mkStructure(i.id))
  }

  protected def defns(struct: CompositeStructure, bases: List[Init] = List.empty): RenderableCogenProduct = {
    val ifDecls = struct.composite.map {
      iface =>
        ctx.conv.toScala(iface).init()
    }

    val superClasses = bases ++ ifDecls

    val tools = struct.t.within(s"${struct.fields.id.name.capitalize}Extensions")

    val qqComposite = q"""case class ${struct.t.typeName}(..${struct.decls}) extends ..$superClasses {}"""

    val qqTools = q""" implicit class ${tools.typeName}(_value: ${struct.t.typeFull}) { }"""

    val qqCompositeCompanion =
      q"""object ${struct.t.termName} {
          ..${struct.constructors}
         }"""

    ctx.ext.extend(struct.fields, CogenProduct(qqComposite, qqCompositeCompanion, qqTools, List.empty), _.handleComposite)
  }

  protected def renderAlias(i: Alias): Seq[Defn] = {
    Seq(q"type ${conv.toScala(i.id).typeName} = ${conv.toScala(i.target).typeFull}")
  }

  protected def renderAdt(i: Adt): RenderableCogenProduct = {
    val t = conv.toScala(i.id)

    val members = i.alternatives.map {
      m =>
        val memberName = m.memberName.getOrElse(m.typeId.name).capitalize
        val mt = t.within(memberName)
        val original = conv.toScala(m.typeId)

        val qqElement = q"""case class ${mt.typeName}(value: ${original.typeAbsolute}) extends ..${List(t.init())}"""
        val qqCompanion = q""" object ${mt.termName} {} """


        val converters = List(
          q"""implicit def ${Term.Name("into" + memberName)}(value: ${original.typeAbsolute}): ${t.typeFull} = ${mt.termFull}(value) """
          ,
          q"""implicit def ${Term.Name("from" + memberName)}(value: ${mt.typeFull}): ${original.typeAbsolute} = value.value"""
        )

        AdtElementProduct(memberName, qqElement, qqCompanion, converters)
    }

    val qqAdt = q""" sealed trait ${t.typeName} extends ${rt.adtEl.init()}{} """
    val qqAdtCompanion =
      q"""object ${t.termName} extends ${rt.adt.init()} {
            import scala.language.implicitConversions

            type Element = ${t.typeFull}

           }"""
    ext.extend(i, AdtProduct(qqAdt, qqAdtCompanion, members), _.handleAdt)
  }

  protected def renderEnumeration(i: Enumeration): RenderableCogenProduct = {
    val t = conv.toScala(i.id)

    val members = i.members.map {
      m =>
        val mt = t.within(m)
        val element =
          q"""case object ${mt.termName} extends ${t.init()} {
              override def toString: String = ${Lit.String(m)}
            }"""

        mt.termName -> element
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
           }"""

    ext.extend(i, EnumProduct(qqEnum, qqEnumCompanion, members), _.handleEnum)
  }


  protected def renderIdentifier(i: Identifier): RenderableCogenProduct = {
    val fields = typespace.structure.structure(i).toScala
    val decls = fields.all.toParams
    val typeName = i.id.name

    val interp = Term.Interpolate(Term.Name("s"), List(Lit.String(typeName + "#"), Lit.String("")), List(Term.Name("suffix")))

    val t = conv.toScala(i.id)
    val tools = t.within(s"${i.id.name}Extensions")

    val qqTools = q"""implicit class ${tools.typeName}(_value: ${t.typeFull}) { }"""

    val parsers = fields.all.zipWithIndex
      .map(fi => q"parsePart[${fi._1.fieldType}](parts(${Lit.Int(fi._2)}), classOf[${fi._1.fieldType}])")

    val superClasses = List(rt.generated.init(), rt.tIDLIdentifier.init())

    val qqCompanion =
      q"""object ${t.termName} {
            def parse(s: String): ${t.typeName} = {
              import ${rt.tIDLIdentifier.termBase}._
              val withoutPrefix = s.substring(s.indexOf("#") + 1)
              val parts = withoutPrefix.split(":").map(part => unescape(part))
              ${t.termName}(..$parsers)
            }
      }"""

    val qqIdentifier =
      q"""case class ${t.typeName} (..$decls) extends ..$superClasses {
            override def toString: String = {
              import ${rt.tIDLIdentifier.termBase}._
              val suffix = this.productIterator.map(part => escape(part.toString)).mkString(":")
              $interp
            }
         }"""


    ext.extend(i, CogenProduct(qqIdentifier, qqCompanion, qqTools, List.empty), _.handleIdentifier)
  }

  protected def renderInterface(i: Interface): RenderableCogenProduct = {
    val fields = typespace.structure.structure(i).toScala
    val decls = fields.all.map {
      f =>
        Decl.Def(List.empty, f.name, List.empty, List.empty, f.fieldType)
    }

    val ifDecls = (rt.generated +: i.struct.superclasses.interfaces.map(conv.toScala)).map(_.init())

    val t = conv.toScala(i.id)
    val eid = typespace.implId(i.id)

    val implStructure = ctx.tools.mkStructure(eid)
    val impl = defns(implStructure).render

    val tools = t.within(s"${i.id.name}Extensions")
    val qqTools = q"""implicit class ${tools.typeName}(_value: ${t.typeFull}) { }"""

    val qqInterface =
      q"""trait ${t.typeName} extends ..$ifDecls {
          ..$decls
          }

       """

    val qqInterfaceCompanion =
      q"""object ${t.termName} {
             def apply(..${implStructure.decls}) = ${conv.toScala(eid).termName}(..${implStructure.names})
             ..$impl
         }"""

    ext.extend(i, CogenProduct(qqInterface, qqInterfaceCompanion, qqTools, List.empty), _.handleInterface)
  }


  protected def renderService(i: Service): RenderableCogenProduct = {
    val typeName = i.id.name

    val t = conv.toScala(IndefiniteId(i.id))

    val serviceInputBase = t.within(s"In${typeName.capitalize}")
    val serviceOutputBase = t.within(s"Out${typeName.capitalize}")

    val decls = i.methods.collect { // TODO

      case method: DeprecatedRPCMethod =>
        // TODO: unify with ephemerals in typespace
        val in = t.within(s"In${method.name.capitalize}")
        val out = t.within(s"Out${method.name.capitalize}")

        val inDef = DTOId(i.id, in.fullJavaType.name)
        val outDef = DTOId(i.id, out.fullJavaType.name)

        val inputComposite = ctx.tools.mkStructure(inDef)
        val outputComposite = ctx.tools.mkStructure(outDef)

        val inputType = in.typeFull
        val outputType = out.typeFull


        val ioDefns = Seq(
          defns(inputComposite, List(serviceInputBase.init()))
          , defns(outputComposite, List(serviceOutputBase.init()))
        )

        ServiceMethodProduct(
          method.name
          , inputComposite
          , outputComposite
          , inputType
          , outputType
          , ioDefns
        )
    }


    val tools = t.within(s"${i.id.name}Extensions")

    val fullService = t.parameterize("R")
    val fullServiceType = fullService.typeFull
    val qqService =
      q"""trait ${t.typeName}[R[_]] extends ${rt.idtService.parameterize("R").init()} {
          override type InputType = ${serviceInputBase.typeFull}
          override type OutputType = ${serviceOutputBase.typeFull}
          override def inputClass: Class[${serviceInputBase.typeFull}] = classOf[${serviceInputBase.typeFull}]
          override def outputClass: Class[${serviceOutputBase.typeFull}] = classOf[${serviceOutputBase.typeFull}]

          ..${decls.map(_.defn)}
         }"""
    val qqTools = q"""implicit class ${tools.typeName}[R[_]](_value: $fullServiceType) {}"""

    val qqServiceCompanion =
      q"""object ${t.termName} {
            sealed trait ${serviceInputBase.typeName} extends Any with ${rt.input.init()} {}
            sealed trait ${serviceOutputBase.typeName} extends Any with ${rt.output.init()} {}

            ..${decls.flatMap(_.types).flatMap(_.render)}
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
           ..$explodedDecls
           }"""
          ,
          q"""class ${dispactherTpe.typeName}[R[+_], S <: $fullServiceType](val service: ${wrapped.typeFull})
              extends ${fullService.init()} with ${rt.generated.init()} {
           ..$transportDecls
           }"""
        )
      }

      List(dServer, dClient, dCompr) ++ dExpl
    }


    val preamble =
      s"""import scala.language.higherKinds
         |import _root_.${rt.runtimePkg}._
         |""".stripMargin

    ext.extend(i, CogenProduct(qqService, qqServiceCompanion, qqTools, dispatchers, preamble), _.handleService)
  }
}
