package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawNodeMeta, RawTopLevelDefn, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import com.github.pshirshov.izumi.idealingua.typer2.{TsProvider, WarnLogger}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.NodeMeta
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{CustomTemplate, Generic}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArg, IzTypeArgName}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.{TemplateArgumentClash, TemplateArgumentsCountMismatch, TemplatedExpected}
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model._
import com.github.pshirshov.izumi.idealingua.typer2.results.TList

import scala.collection.mutable

class TemplateSupport(
                       contextProducer: ContextProducer,
                       context: Interpreter.Args,
                       i2: TypedefSupport,
                       resolvers: Resolvers,
                       logger: WarnLogger,
                       provider: TsProvider,
                     ) {


  def makeTemplate(t: RawTypeDef.Template): TList = {
    val id = resolvers.nameToTopId(t.decl.id)
    val args = t.arguments.map(arg => IzTypeArgName(arg.name))
    val badNames = args.groupBy(_.name).filter(_._2.size > 1)

    for {
      _ <- if (badNames.nonEmpty) {
        Left(List(TemplateArgumentClash(id, badNames.values.flatten.toSet)))
      } else {
        Right(())
      }

    } yield {
      List(CustomTemplate(id, args, t.decl))
    }
  }

  def makeInstance(v: RawTypeDef.Instance): TList = {
    val ctxInstances = mutable.HashMap[IzTypeId, ProcessedOp]()
    val ref = resolvers.resolve(v.source)

    makeInstance(v.id, ref, v.meta, ctxInstances).map(i => i ++ ctxInstances.values.map(_.member))
  }


  def makeInstance(id: RawDeclaredTypeName, source: IzTypeReference, meta: RawNodeMeta, ephemerals: mutable.HashMap[IzTypeId, ProcessedOp]): TList = {
    val template = source match {
      case IzTypeReference.Scalar(tid) =>
        provider.freeze()(tid)
      case IzTypeReference.Generic(tid, _, _) =>
        provider.freeze()(tid)
    }

    val targs = source match {
      case IzTypeReference.Scalar(_) =>
        Seq.empty
      case IzTypeReference.Generic(_, args, _) =>
        args
    }


    for {
      tdef <- template match {
        case ProcessedOp.Exported(member: CustomTemplate) =>
          Right(member)
        case o =>
          Left(List(TemplatedExpected(source, o.member)))
      }
      _ <- if (tdef.args.size != targs.size) {
        Left(List(TemplateArgumentsCountMismatch(tdef.id, tdef.args.size, targs.size)))
      } else {
        Right(())
      }

      argsMap = tdef.args.zip(targs)

      templateContext = instantiateArgs(ephemerals, meta)(argsMap)


      withFixedId = tdef.decl match {
        case i: RawTypeDef.Interface =>
          i.copy(id = id, meta = meta)
        case d: RawTypeDef.DTO =>
          d.copy(id = id, meta = meta)
        case a: RawTypeDef.Adt =>
          a.copy(id = id, meta = meta)
      }

      isub = contextProducer.remake(ephemerals.toMap, context.copy(templateContext)).interpreter
      instance <- isub.dispatch(RawTopLevelDefn.TLDBaseType(withFixedId))
    } yield {
      instance
    }
  }

  private def instantiateArgs(ephemerals: mutable.HashMap[IzTypeId, ProcessedOp], m: RawNodeMeta)(targs: Seq[(IzTypeArgName, IzTypeArg)]): Map[IzTypeArgName, IzTypeReference] = {
    import Tools._

    targs
      .map {
        case (name, arg) =>
          val argt = arg.value.ref match {
            case ref: IzTypeReference.Scalar =>
              ref

            case ref@IzTypeReference.Generic(tid, _, _) =>
              provider.freeze()(tid).member match {
                case generic: Generic =>
                  generic match {
                    case _: IzType.BuiltinGeneric =>
                      ref

                    case g =>
                      val tmpName: RawDeclaredTypeName = genericName(ref, g, i2.meta(m))
                      val ephemeralId: IzTypeId = resolvers.nameToId(tmpName, Seq.empty)

                      if (!ephemerals.contains(ephemeralId)) {
                        val i = makeInstance(tmpName, ref, m, ephemerals)
                        i match {
                          case Left(value) =>
                            fail(s"ephemeral instantiation failed: $value")
                          case Right(value) =>
                            value.foreach {
                              v =>
                                //println(s"registering instance ${v.id}")

                                assert(!ephemerals.contains(v.id), s"BUG in generic machinery: ephemeral ${v.id} is already registered")
                                ephemerals.put(v.id, ProcessedOp.Exported(v))

                            }

                        }
                      }

                      IzTypeReference.Scalar(ephemeralId)
                  }
                case o =>
                  fail(s"reference $ref must point to generic, but we got $o")
              }
          }

          name -> argt
      }
      .toMap
  }

  private def genericName(ref: IzTypeReference.Generic, g: Generic, meta: NodeMeta): RawDeclaredTypeName = {
    val name = resolvers.genericName(ref)
    if (ref.adhocName.isEmpty) {
      logger.log(T2Warn.TemplateInstanceNameWillBeGenerated(g.id, name.name, meta))
    }
    name
  }
}
