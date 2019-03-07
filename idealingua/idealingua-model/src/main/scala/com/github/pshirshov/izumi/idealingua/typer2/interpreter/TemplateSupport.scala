package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawNodeMeta, RawTopLevelDefn, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import com.github.pshirshov.izumi.idealingua.typer2.{TsProvider, WarnLogger}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.NodeMeta
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{CustomTemplate, Generic}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArg, IzTypeArgName}
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

  import Tools._

  def makeTemplate(t: RawTypeDef.Template): TList = {
    val id = resolvers.nameToTopId(t.decl.id)
    val badNames = t.arguments.groupBy(_.name).filter(_._2.size > 1)
    assert(badNames.isEmpty, s"Template argument names clashed: $badNames")

    val args1 = t.arguments.map(arg => IzTypeArgName(arg.name))
    Right(List(CustomTemplate(id, args1, t.decl)))
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

    val t = template match {
      case ProcessedOp.Exported(member: CustomTemplate) =>
        member
      case o =>
        fail(s"$source must point to a template, but we got $o")
    }

    val targs = source match {
      case IzTypeReference.Scalar(_) =>
        Seq.empty
      case IzTypeReference.Generic(_, args, _) =>
        args
    }
    assert(t.args.size == targs.size)
    val argsMap = t.args.zip(targs)

    val templateContext = instantiateArgs(ephemerals, meta)(argsMap)


    val withFixedId = t.decl match {
      case i: RawTypeDef.Interface =>
        i.copy(id = id, meta = meta)
      case d: RawTypeDef.DTO =>
        d.copy(id = id, meta = meta)
      case a: RawTypeDef.Adt =>
        a.copy(id = id, meta = meta)
    }

    val isub = contextProducer.remake(ephemerals.toMap, context.copy(templateContext)).interpreter
    val instance = isub.dispatch(RawTopLevelDefn.TLDBaseType(withFixedId))
    instance
  }

  private def instantiateArgs(ephemerals: mutable.HashMap[IzTypeId, ProcessedOp], m: RawNodeMeta)(targs: Seq[(IzTypeArgName, IzTypeArg)]): Map[IzTypeArgName, IzTypeReference] = {
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
