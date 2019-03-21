package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.idealingua.model.common.{ConstId, DomainId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn.TLDConsts
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Operation, TypenameRef, TyperFailure}
import com.github.pshirshov.izumi.idealingua.typer2.indexing.{DomainIndex, TopLevelTypeIndexer}
import com.github.pshirshov.izumi.idealingua.typer2.interpreter.{ConstRecorder, Interpreter, InterpreterContext}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.NodeMeta
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.{IzDomainPath, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.RefToTLTLink
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model._
import com.github.pshirshov.izumi.idealingua.typer2.results._

import scala.collection.mutable

trait RefRecorder {
  def require(ref: RefToTLTLink): Unit

  def requireNow(ref: RefToTLTLink): Either[List[BuilderFail], Unit]
}

trait TsProvider {
  def freeze(): Map[IzTypeId, ProcessedOp]

  def freezeConsts(): Seq[TLDConsts]

  def get(id: IzTypeId, context: IzTypeId, meta: NodeMeta): Either[List[BuilderFail], ProcessedOp] = {
    freeze().get(id) match {
      case Some(value) =>
        Right(value)
      case None =>
        Left(List(MissingTypespaceMember(id, context, meta)))
    }
  }
}

class Ts2Builder(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex]) extends WarnLogger.WarnLoggerImpl with RefRecorder with ConstRecorder with TsProvider {
  private val failed = mutable.HashSet.empty[TypenameRef]
  private val failures = mutable.ArrayBuffer.empty[BuilderFail]
  private val existing = mutable.HashSet.empty[TypenameRef]
  private val types = mutable.HashMap[IzTypeId, ProcessedOp]()
  private val consts = mutable.HashMap[TypedConstId, RawConst]()
  private val thisPrefix = TypePrefix.UserTLT(IzPackage(index.defn.id.toPackage.map(IzDomainPath)))


  override def freeze(): Map[IzTypeId, ProcessedOp] = types.toMap

  override def freezeConsts(): Seq[TLDConsts] = {
    consts
      .toList
      .groupBy(_._1.scope)
      .map {
        case (scope, v) =>
          TLDConsts(RawConstBlock(scope, v.map(_._2)))
      }
      .toSeq
  }

  def defined: Set[TypenameRef] = {
    existing.toSet
  }

  def add(ops: Operation): Unit = {
    ops match {
      case Typer2.DefineBuiltin(id) =>
        register(ops, Right(List(index.builtins(id))))

      case single: Typer2.DefineType =>
        val dindex = if (single.main.source == index.defn.id) {
          index
        } else {
          importedIndexes(single.main.source)
        }

        val interpreter = makeInterpreter(dindex).interpreter

        val product = interpreter.dispatch(single.main.defn)
        register(ops, product)

      case mult: Typer2.DefineWithDecls =>
        merge(mult) match {
          case Left(value) =>
            fail(ops, value)
          case Right(value) =>
            add(value)
        }
    }
  }


  override def nextIndex(): Int = consts.size + 1

  override def registerConst(id: TypedConstId, value: RawVal, position: InputPosition): Unit = {
    if (consts.contains(id)) {
      throw new IllegalStateException(s"Unexpected problem: sanity check failed for const $id")
    }

    Quirks.discard(consts.put(id, RawConst(ConstId(id.name), value, RawConstMeta(None, position))))
  }

  def merge(mult: Typer2.DefineWithDecls): Either[List[BuilderFail], Typer2.DefineType] = {
    if (mult.decls.isEmpty) {
      Right(Typer2.DefineType(mult.id, mult.depends, mult.main))
    } else {
      Left(List(FeatureUnsupported(null, "TODO", null)))
    }
  }

  def fail(ops: Operation, failures: List[BuilderFail]): Unit = {
    if (ops.depends.exists(failed.contains)) {
      // dependency has failed already, fine to skip
    } else {
      failed.add(ops.id)
      this.failures ++= failures
    }
  }


  def finish(): Either[TyperFailure, Ts2Builder.Output] = {
    (for {
      _ <- if (failures.nonEmpty) {
        Left(failures.toList)
      } else {
        Right(())
      }
      _ <- instantiatePostponedGenerics(missingRefs.toSet)
      verifier = makeVerifier()
      allTypes = freezeTypes()
      allConsts = freezeConsts()
      _ <- verifier.validateTypespace(allTypes)
    } yield {
      Ts2Builder.Output(Typespace2(
        index.defn.id,
        index.defn.imports,
        allWarnings.toList,
        Set.empty,
        this.types.values.toList,
        List.empty,
        List.empty,
        index.defn.origin,
      ), allConsts)
    }) match {
      case Left(f) =>
        Left(TyperFailure(f, allWarnings.toList))
      case Right(s) =>
        Right(s)
    }
  }

  private val missingRefs = mutable.ArrayBuffer[RefToTLTLink]()

  override def require(ref: RefToTLTLink): Unit = {
    missingRefs += ref
  }


  override def requireNow(ref: RefToTLTLink): Either[List[BuilderFail], Unit] = {
    instantiatePostponedGenerics(Set(ref))
  }

  def instantiatePostponedGenerics(toCreate: Set[RefToTLTLink]): Either[List[BuilderFail], Unit] = {
    val ret = toCreate
      .map {
        mg =>
          makeInterpreter(index).templates.makeInstance(RawDeclaredTypeName(mg.target.name.name), mg.ref, RawNodeMeta(Seq.empty, Seq.empty, InputPosition.Undefined), mutable.HashMap.empty)
      }
      .toList
      .biFlatAggregate

    registerTypes(ret)
  }

  private def freezeTypes(): List[IzType] = {
    Ts2Builder.this.types.values.map(_.member).toList
  }


  private def makeVerifier(): TsVerifier = {
    new TsVerifier(types.toMap, makeEvaluator(), this)
  }

  private def makeEvaluator(): TopLevelTypeIndexer = {
    new TopLevelTypeIndexer(makeInterpreter(index).resolvers)
  }

  private def makeInterpreter(dindex: DomainIndex): InterpreterContext = {
    new InterpreterContext(dindex, this, this, this, this, Interpreter.Args(Map.empty))
  }

  private def register(ops: Operation, maybeTypes: Either[List[BuilderFail], List[IzType]]): Unit = {
    registerTypes(maybeTypes) match {
      case Left(value) =>
        fail(ops, value)

      case Right(_) =>
        existing.add(ops.id).discard()

    }
  }

  private def checkSanity(maybeTypes: List[IzType]): Either[List[BuilderFail], Unit] = {
    for {
      typesToRegister <- Right(maybeTypes)
      toRegister = typesToRegister.groupBy(_.id).mapValues(_.toSet)
      badRegistrations = toRegister.filter(_._2.size > 1)
      _ <- if (badRegistrations.nonEmpty) {
        Left(List(TypesAlreadyRegistered(badRegistrations.keySet.map(_.name))))
      } else {
        Right(())
      }
      conflicts = toRegister.mapValues(_.head).filter {
        case (id, tpe) =>
          val maybeOp = types.get(id)
          maybeOp.nonEmpty && !maybeOp.exists(_.member == tpe)
      }
      _ <- if (conflicts.nonEmpty) {
        Left(List(TypesAlreadyRegistered(conflicts.keySet.map(_.name))))
      } else {
        Right(())
      }
    } yield {
    }
  }

  private def registerTypes(maybeTypes: Either[List[BuilderFail], List[IzType]]): Either[List[BuilderFail], Unit] = {
    for {
      typesToRegister <- maybeTypes
      verifier = makeVerifier()
      _ <- verifier.prevalidateTypes(typesToRegister)
      _ <- checkSanity(typesToRegister)
    } yield {
      typesToRegister.foreach {
        product =>
          types.put(product.id, makeMember(product)) match {
            case Some(value) if value.member != product =>
              throw new IllegalStateException(s"Unexpected problem: sanity check failed for product $product != $value")
            case _ =>
          }
      }
    }
  }

  private def isOwn(id: IzTypeId): Boolean = {
    id match {
      case IzTypeId.BuiltinTypeId(_) =>
        false
      case IzTypeId.UserTypeId(prefix, _) =>
        prefix == thisPrefix
    }
  }

  private def makeMember(member: IzType): ProcessedOp = {
    if (isOwn(member.id)) {
      ProcessedOp.Exported(member)
    } else {
      ProcessedOp.Imported(member)
    }
  }
}

object Ts2Builder {
  case class Output(ts: Typespace2, consts: Seq[TLDConsts])
}
