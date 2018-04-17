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
import com.github.pshirshov.izumi.idealingua.translator.toscala.products._
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

  protected def renderAdt(i: Adt, bases: List[Init] = List.empty): RenderableCogenProduct = {
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

    val superClasses = List(rt.adtEl.init()) ++ bases
    val qqAdt = q""" sealed trait ${t.typeName} extends ..$superClasses {} """
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
    val sp = ServiceContext(ctx, svc)
    val decls = svc.methods.collect({ case c: RPCMethod => c })
      .map(ServiceMethodProduct(ctx, sp, _))

    val inputs = decls.map(_.inputIdWrapped).map({
      dto =>
        val struct = ctx.tools.mkStructure(dto)
        defns(struct, List(sp.serviceInputBase.init()))
    }).flatMap(_.render)

    val outputs = decls.map(_.outputIdWrapped).map({
      case dto: DTOId =>
        val struct = ctx.tools.mkStructure(dto)
        defns(struct, List(sp.serviceOutputBase.init()))
      case adt: AdtId =>
        renderAdt(typespace.apply(adt).asInstanceOf[Adt], List(sp.serviceOutputBase.init()))
      case o =>
        throw new IDLException(s"Impossible case: $o")
    }).flatMap(_.render)

    val inputMappers = decls.map(_.inputMatcher)
    val outputMappers = decls.map(_.outputMatcher)
    val packing = decls.map(_.bodyClient)
    val unpacking = decls.map(_.bodyServer)
    val dispatchers = decls.map(_.dispatching)

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

    val qqUnpackingDispatcher =
      q"""
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
        class Impl[R[_] : ServiceResult, C](val service: ${sp.svcTpe.typeFull}[R, C]) extends UnpackingDispatcher[R, C] {
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
                   val id: String = ${Lit.String(s"${sp.svcId.name}.SafeToUnsafeBridge.name)")}
                   throw new TypeMismatchException(s"Unexpected output in $$id: $$o", o)
               }
             }
           }
       """

    val qqWrappedCompanion =
      q"""
         object ${sp.svcWrappedTpe.termName}
          extends IdentifiableServiceDefinition
            with WrappedServiceDefinition
            with WrappedUnsafeServiceDefinition {

          type Input =  ${sp.serviceInputBase.typeFull}
          type Output = ${sp.serviceOutputBase.typeFull}


           override type ServiceServer[R[_], C] = ${sp.svcTpe.parameterize("R", "C").typeFull}
           override type ServiceClient[R[_]] = ${sp.svcClientTpe.parameterize("R").typeFull}

  def client[R[_] : ServiceResult](dispatcher: Dispatcher[${sp.serviceInputBase.typeFull}, ${sp.serviceOutputBase.typeFull}, R]): ${sp.svcClientTpe.parameterize("R").typeFull} = {
    new PackingDispatcher.Impl[R](dispatcher)
  }


  def clientUnsafe[R[_] : ServiceResult](dispatcher: Dispatcher[MuxRequest[_], MuxResponse[_], R]): ${sp.svcClientTpe.parameterize("R").typeFull} = {
    client(new SafeToUnsafeBridge[R](dispatcher))
  }

  def server[R[_] : ServiceResult, C](service: ${sp.svcTpe.parameterize("R", "C").typeFull}): Dispatcher[InContext[${sp.serviceInputBase.typeFull}, C], ${sp.serviceOutputBase.typeFull}, R] = {
    new UnpackingDispatcher.Impl[R, C](service)
  }


  def serverUnsafe[R[_] : ServiceResult, C](service: ${sp.svcTpe.parameterize("R", "C").typeFull}): UnsafeDispatcher[C, R] = {
    new UnpackingDispatcher.Impl[R, C](service)
  }

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

    val input = CogenPair(q"sealed trait ${sp.serviceInputBase.typeName} extends Any", q"object ${sp.serviceInputBase.termName} {}")
    val output = CogenPair(q"sealed trait ${sp.serviceOutputBase.typeName} extends Any", q"object ${sp.serviceOutputBase.termName} {}")

    // TODO: move Any into extensions
    val qqBaseCompanion =
      q"""
         object ${sp.svcBaseTpe.termName} {

           ..$outputs
           ..$inputs
         }
       """


    val out = CogenServiceProduct(
      CogenPair(qqService, qqServiceCompanion)
      , CogenPair(qqClient, qqClientCompanion)
      , CogenPair(qqWrapped, qqWrappedCompanion)
      , products.CogenServiceDefs(qqBaseCompanion, input, output)
      , List(runtime.Import.from(runtime.Pkg.language, "higherKinds"), rt.services.`import`)
    )

    ext.extend(FullServiceContext(sp, decls), out, _.handleService)

  }
}
