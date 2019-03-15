package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawNodeMeta, RawTopLevelDefn, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.NodeMeta
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{CustomTemplate, ForeignGeneric, Generic}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArgValue, IzTypeArgName}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model._
import com.github.pshirshov.izumi.idealingua.typer2.results._
import com.github.pshirshov.izumi.idealingua.typer2.{TsProvider, WarnLogger}

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


  def makeInstance(name: RawDeclaredTypeName, source: IzTypeReference, instanceRawMeta: RawNodeMeta, ephemerals: mutable.HashMap[IzTypeId, ProcessedOp]): TList = {
    val instanceMeta = i2.meta(instanceRawMeta)
    val instanceId = resolvers.nameToId(name, Seq.empty)
    for {
      template <- source match {
        case IzTypeReference.Scalar(tid) =>
          provider.get(tid, instanceId, instanceMeta)
        case IzTypeReference.Generic(tid, _, _) =>
          provider.get(tid, instanceId, instanceMeta)
      }

      targs = source match {
        case IzTypeReference.Scalar(_) =>
          Seq.empty
        case IzTypeReference.Generic(_, args, _) =>
          args
      }
      tdef <- template match {
        case ProcessedOp.Exported(member: CustomTemplate) =>
          Right(member)
        case ProcessedOp.Exported(member: ForeignGeneric) =>
          Right(member)
        case o =>
          Left(List(TemplateExpected(source, o.member)))
      }
      _ <- if (tdef.args.size != targs.size) {
        Left(List(TemplateArgumentsCountMismatch(tdef.id, tdef.args.size, targs.size)))
      } else {
        Right(())
      }

      argsMap = tdef.args.zip(targs)

      templateContext <- instantiateArgs(ephemerals, instanceRawMeta)(argsMap)
      isub = contextProducer.remake(ephemerals.toMap, context.copy(templateContext)).interpreter

      instance <- tdef match {
        case c: CustomTemplate =>
          val fullMeta = instanceRawMeta.copy(doc = c.decl.meta.doc ++ instanceRawMeta.doc)
          val withFixedId = c.decl match {
            case i: RawTypeDef.Interface =>
              i.copy(id = name, meta = fullMeta)
            case d: RawTypeDef.DTO =>
              d.copy(id = name, meta = fullMeta)
            case a: RawTypeDef.Adt =>
              a.copy(id = name, meta = fullMeta)
            case s: RawTypeDef.RawService =>
              s.copy(id = name, meta = fullMeta)
            case b: RawTypeDef.RawBuzzer =>
              b.copy(id = name, meta = fullMeta)
            case b: RawTypeDef.RawStreams =>
              b.copy(id = name, meta = fullMeta)
          }
          isub.dispatch(RawTopLevelDefn.TLDBaseType(withFixedId))
        case t: ForeignGeneric =>
          Right(List(IzType.IzAlias(instanceId, source, instanceMeta.copy(doc = t.meta.doc ++ instanceMeta.doc))))
        case _ =>
          Left(List(UnexpectedException(new IllegalStateException(s"BUG: $tdef has unexpected type but that can't be"))))
      }
    } yield {
      instance
    }
  }

  private def instantiateArgs(ephemerals: mutable.HashMap[IzTypeId, ProcessedOp], m: RawNodeMeta)(targs: Seq[(IzTypeArgName, IzTypeArgValue)]): Either[List[BuilderFail], Map[IzTypeArgName, IzTypeReference]] = {

    val maybeArgValues = targs
      .map {
        case (name, arg) =>
          val argt: Either[List[BuilderFail], IzTypeReference] = arg.ref match {
            case ref: IzTypeReference.Scalar =>
              Right(ref)

            case ref@IzTypeReference.Generic(tid, _, _) =>
              provider.freeze()(tid).member match {
                case generic: Generic =>
                  generic match {
                    case _: IzType.BuiltinGeneric =>
                      Right(ref)

                    case g =>
                      val tmpName: RawDeclaredTypeName = genericName(ref, g, i2.meta(m))
                      val ephemeralId: IzTypeId = resolvers.nameToId(tmpName, Seq.empty)

                      for {
                        _ <- if (!ephemerals.contains(ephemeralId)) {
                          for {
                            products <- makeInstance(tmpName, ref, m, ephemerals)
                            _ <- products.map {
                              i =>
                                ephemerals.get(i.id) match {
                                  case Some(_) =>
                                    Left(List(DuplicatedTypespaceMembers(Set(i.id))))
                                  case None =>
                                    ephemerals.put(i.id, ProcessedOp.Exported(i))
                                    Right(())
                                }
                            }.biAggregate
                          } yield {

                          }
                        } else {
                          Right(())
                        }
                      } yield {
                        IzTypeReference.Scalar(ephemeralId)
                      }


                  }
                case o =>
                  Left(List(GenericExpected(ref, o)))
              }
          }

          argt
            .map {
              value =>
                name -> value

            }
      }

    for {
      args <- maybeArgValues.biAggregate
    } yield {
      args.toMap
    }
  }

  private def genericName(ref: IzTypeReference.Generic, g: Generic, meta: NodeMeta): RawDeclaredTypeName = {
    val name = resolvers.genericName(ref)
    if (ref.adhocName.isEmpty) {
      logger.log(T2Warn.TemplateInstanceNameWillBeGenerated(g.id, name.name, meta))
    }
    name
  }
}
