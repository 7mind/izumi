package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, DTOId}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Service, TypeDef}
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions._
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.{AdtElementProduct, AdtProduct, EnumProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.{CogenProduct, RenderableCogenProduct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.types._

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
        val memberName = m.name
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

    val sortedFields = fields.all.sortBy(_.field.field.name)

    val parsers = sortedFields.zipWithIndex
      .map(fi => q"parsePart[${fi._1.fieldType}](parts(${Lit.Int(fi._2)}), classOf[${fi._1.fieldType}])")

    val parts = sortedFields.map(fi => q"this.${fi.name}")

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
              val suffix = Seq(..$parts).map(part => escape(part.toString)).mkString(":")
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


  protected def renderService(svc: Service): RenderableCogenProduct = {


    val sp = ServiceProduct(ctx, svc)
    val decls = svc.methods.collect({ case c: RPCMethod => c })
      .map(ServiceMethodProduct(ctx, sp, _))

    val inputs = decls.map(_.inputWrappedId).map({
      dto =>
        val struct = ctx.tools.mkStructure(dto)
        defns(struct, List(sp.serviceInputBase.init()))
    }).flatMap(_.render)

    val outputs = decls.map(_.outputWrappedId).map({
      case dto: DTOId =>
        val struct = ctx.tools.mkStructure(dto)
        defns(struct, List(sp.serviceOutputBase.init()))
      case adt: AdtId =>
        renderAdt(typespace.apply(adt).asInstanceOf[Adt])
      case o =>
        throw new IDLException(s"Impossible case: $o")
    }).flatMap(_.render)

    val inputMappers: List[Case] = decls.map {
      d =>
        p""" case _: ${d.inputWrappedType.typeFull} => Method(serviceId, MethodId(${Lit.String(d.method.name)})) """
    }
    val outputMappers: List[Case] = decls.map {
      d =>
        p""" case _: ${d.outputTypeWrapped.typeFull} => Method(serviceId, MethodId(${Lit.String(d.method.name)})) """
    }

    val packing: List[Defn] = List.empty
    val unpacking: List[Defn] = List.empty

    val dispatchers: List[Case] = decls.map {
      d =>
        p""" case InContext(v: ${d.inputWrappedType.typeFull}, c) => _ServiceResult.map(${Term.Name(d.method.name)}(c, v))(v => v) // upcast """
    }

    val qqService =
      q"""trait ${sp.svcTpe.typeName}[R[_], C] extends ${rt.WithResultType.parameterize("R").init()} {
            ..${decls.map(_.defnServer)}
          }"""

    val qqClient =
      q"""trait ${sp.svcClientTpe.typeName}[R[_]] extends ${rt.WithResultType.parameterize("R").init()} {
            ..${decls.map(_.defnClient)}
          }"""

    val qqClientCompanion =
      q"""object ${sp.svcClientTpe.termName} {

         }"""

    val qqWrapped =
      q"""trait ${sp.svcWrappedTpe.typeName}[R[_], C] extends ${rt.WithResultType.parameterize("R").init()} {
            import ${sp.svcWrappedTpe.termBase}._

            ..${decls.map(_.defnWrapped)}
          }"""

    val qqPackingDispatcher =
      q"""
           trait PackingDispatcher[R[_]]
             extends ${sp.svcClientTpe.parameterize("R").init()}
               with ${rt.WithResult.parameterize("R").init()} {
             def dispatcher: Dispatcher[${sp.serviceInputBase.typeFull}, ${sp.serviceOutputBase.typeFull}, R]

               ..$packing
           }
       """
    val qqPackingDispatcherCompanion =
      q"""  object PackingDispatcher {
        class Impl[R[_] : ServiceResult](val dispatcher: Dispatcher[${sp.serviceInputBase.typeFull}, ${sp.serviceOutputBase.typeFull}, R]) extends PackingDispatcher[R] {
          override protected def _ServiceResult: ServiceResult[R] = implicitly
        }
      }"""

    val qqUnpackingDispatcher = q"""
  trait UnpackingDispatcher[R[_], C]
    extends ${sp.svcWrappedTpe.parameterize("R", "C").init()}
      with Dispatcher[InContext[${sp.serviceInputBase.typeFull}, C], ${sp.serviceOutputBase.typeFull}, R]
      with UnsafeDispatcher[C, R]
      with WithResult[R] {
    def service: ${sp.svcTpe.typeFull}[R, C]

    def dispatch(input: InContext[${sp.serviceInputBase.typeFull}, C]): Result[${sp.serviceOutputBase.typeFull}] = {
      input match {
        ..case $dispatchers
      }
    }

    def identifier: ServiceId = serviceId

    def dispatchUnsafe(input: InContext[MuxRequest[_], C]): Option[Result[MuxResponse[_]]] = {
      input.value.v match {
        case v: ${sp.serviceInputBase.typeFull} =>
          Option(_ServiceResult.map(dispatch(InContext(v, input.context)))(v => MuxResponse(v, toMethodId(v))))

        case _ =>
          None
      }
    }

      ..$unpacking
  }"""

    val qqUnpackingDispatcherCompanion =
      q"""  object UnpackingDispatcher {
        class Impl[R[_] : ServiceResult, C](val dispatcher: Dispatcher[${sp.serviceInputBase.typeFull}, ${sp.serviceOutputBase.typeFull}, R]) extends UnpackingDispatcher[R, C] {
          override protected def _ServiceResult: ServiceResult[R] = implicitly
        }
      }"""

    val qqSafeToUnsafeBridge =
      q"""
        class SafeToUnsafeBridge[R[_] : ServiceResult](dispatcher: Dispatcher[MuxRequest[_], MuxResponse[_], R])
           extends Dispatcher[${sp.serviceInputBase.typeFull}, ${sp.serviceOutputBase.typeFull}, R]
           with ${rt.WithResult.parameterize("R").init()} {
             override protected def _ServiceResult: ServiceResult[R] = implicitly

             import ServiceResult._

             override def dispatch(input: ${sp.serviceInputBase.typeFull}): Result[${sp.serviceOutputBase.typeFull}] = {
               dispatcher.dispatch(MuxRequest(input, toMethodId(input))).map {
                 case MuxResponse(t: ${sp.serviceOutputBase.typeFull}, _) =>
                   t
                 case o =>
                   throw new TypeMismatchException(s"Unexpected output in CalculatorServiceSafeToUnsafeBridge.dispatch: $$o", o)
               }
             }
           }
       """

    val qqWrappedCompanion =
      q"""
         object ${sp.svcWrappedTpe.termName} {
          val serviceId = ServiceId(${Lit.String(sp.svcId.name)})

          def toMethodId(v: ${sp.serviceInputBase.typeFull}): Method = {
            v match {
              ..case $inputMappers
            }
          }

          def toMethodId(v: ${sp.serviceOutputBase.typeFull}): Method = {
            v match {
              ..case $outputMappers
            }
          }

          $qqPackingDispatcher
          $qqPackingDispatcherCompanion
          $qqSafeToUnsafeBridge
          $qqUnpackingDispatcher
          $qqUnpackingDispatcherCompanion
         }
       """

    val qqServiceCompanion =
      q"""
         object ${sp.svcTpe.termName} {
         }
       """

    /*
    *             sealed trait ${sp.serviceInputBase.typeName} extends Any with ${rt.input.init()} {}
            sealed trait ${sp.serviceOutputBase.typeName} extends Any with ${rt.input.init()} {}*/
    val qqBaseCompanion =
      q"""
         object ${sp.svcBaseTpe.termName} {
            sealed trait ${sp.serviceInputBase.typeName} extends AnyRef with ${rt.input.init()} {}
            sealed trait ${sp.serviceOutputBase.typeName} extends AnyRef with ${rt.input.init()} {}
           ..$outputs
           ..$inputs
         }
       """

    new RenderableCogenProduct {
      override def preamble: String =
        s"""import scala.language.higherKinds
           |import _root_.${rt.transport.pkg}._
           |import _root_.${rt.services.pkg}._
           |""".stripMargin

      override def render: List[Defn] = List(
        qqService, qqServiceCompanion
        , qqClient, qqClientCompanion
        , qqWrapped, qqWrappedCompanion
        , qqBaseCompanion
      )
    }

    //    val tools = t.within(s"${i.id.name}Extensions")
    //
    //    val fullService = t.parameterize("R")
    //    val fullServiceType = fullService.typeFull
    //    val qqService =
    //      q"""trait ${t.typeName}[R[_]] extends ${rt.idtService.parameterize("R").init()} {
    //          override type InputType = ${serviceInputBase.typeFull}
    //          override type OutputType = ${serviceOutputBase.typeFull}
    //          override def inputClass: Class[${serviceInputBase.typeFull}] = classOf[${serviceInputBase.typeFull}]
    //          override def outputClass: Class[${serviceOutputBase.typeFull}] = classOf[${serviceOutputBase.typeFull}]
    //
    //          ..${decls.map(_.defn)}
    //         }"""
    //    val qqTools = q"""implicit class ${tools.typeName}[R[_]](_value: $fullServiceType) {}"""
    //
    //    val qqServiceCompanion =
    //      q"""object ${t.termName} {
    //            sealed trait ${serviceInputBase.typeName} extends Any with ${rt.input.init()} {}
    //            sealed trait ${serviceOutputBase.typeName} extends Any with ${rt.output.init()} {}
    //
    //            ..${decls.flatMap(_.types).flatMap(_.render)}
    //           }"""
    //
    //
    //    val dispatchers = {
    //      val dServer = {
    //        val forwarder = Term.Match(Term.Name("input"), decls.map(_.routingClause))
    //        val transportDecls =
    //          List(
    //            q"override def dispatch(input: ${serviceInputBase.typeFull}): R[${serviceOutputBase.typeFull}] = $forwarder"
    //          )
    //        val dispatcherInTpe = rt.serverDispatcher.parameterize("R", "S").init()
    //        val dispactherTpe = t.sibling(typeName + "ServerDispatcher")
    //        q"""class ${dispactherTpe.typeName}[R[+_], S <: $fullServiceType]
    //            (
    //              val service: S
    //            ) extends $dispatcherInTpe with ${rt.generated.init()} {
    //            ..$transportDecls
    //           }"""
    //      }
    //
    //
    //      val dClient = {
    //        val dispatcherInTpe = rt.clientDispatcher.parameterize("R", "S")
    //        val dispactherTpe = t.sibling(typeName + "ClientDispatcher")
    //
    //        val transportDecls = decls.map(_.defnDispatch)
    //
    //        q"""class ${dispactherTpe.typeName}[R[+_], S <: $fullServiceType]
    //            (
    //              dispatcher: ${dispatcherInTpe.typeFull}
    //            ) extends ${fullService.init()} with ${rt.generated.init()} {
    //           ..$transportDecls
    //           }"""
    //      }
    //
    //      val dCompr = {
    //        val dispatcherInTpe = rt.clientWrapper.parameterize("R", "S")
    //
    //        val forwarder = decls.map(_.defnCompress)
    //
    //        val dispactherTpe = t.sibling(typeName + "ClientWrapper")
    //        q"""class ${dispactherTpe.typeName}[R[+_], S <: $fullServiceType]
    //            (
    //              val service: S
    //            ) extends ${dispatcherInTpe.init()} with ${rt.generated.init()} {
    //            ..$forwarder
    //           }"""
    //      }
    //
    //      val dExpl = {
    //        val explodedService = t.sibling(typeName + "Unwrapped")
    //        val explodedDecls = decls.flatMap(in => Seq(in.defnExploded))
    //
    //
    //        val dispactherTpe = t.sibling(typeName + "ServerWrapper")
    //
    //        val transportDecls = decls.flatMap(in => Seq(in.defnExplode))
    //
    //        val wrapperTpe = rt.serverWrapper.parameterize("R", "S")
    //        val wrapped = explodedService.parameterize("R", "S")
    //
    //        Seq(
    //          q"""trait ${explodedService.typeName}[R[+_], S <: $fullServiceType]
    //              extends ${wrapperTpe.init()} with ${rt.generated.init()} {
    //           ..$explodedDecls
    //           }"""
    //          ,
    //          q"""class ${dispactherTpe.typeName}[R[+_], S <: $fullServiceType](val service: ${wrapped.typeFull})
    //              extends ${fullService.init()} with ${rt.generated.init()} {
    //           ..$transportDecls
    //           }"""
    //        )
    //      }
    //
    //      List(dServer, dClient, dCompr) ++ dExpl
    //    }
    //
    //

    //
    //
    //    ext.extend(i, CogenProduct(qqService, qqServiceCompanion, qqTools, dispatchers, preamble), _.handleService)

  }
}
