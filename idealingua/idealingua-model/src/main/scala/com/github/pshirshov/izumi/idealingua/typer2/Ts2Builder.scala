package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawStructure, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawGenericRef, RawRef}
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Operation, TypenameRef}
import com.github.pshirshov.izumi.idealingua.typer2.interpreter.{Interpreter, InterpreterContext}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.{IzDomainPath, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp.Exported
import com.github.pshirshov.izumi.idealingua.typer2.model._

import scala.collection.{immutable, mutable}


class Ts2Builder(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex]) extends WarnLogger {
  private val failed = mutable.HashSet.empty[TypenameRef]
  private val failures = mutable.ArrayBuffer.empty[BuilderFail]
  private val warnings = mutable.ArrayBuffer.empty[T2Warn]
  private val existing = mutable.HashSet.empty[TypenameRef]
  private val types = mutable.HashMap[IzTypeId, ProcessedOp]()
  private val thisPrefix = TypePrefix.UserTLT(IzPackage(index.defn.id.toPackage.map(IzDomainPath)))


  override def log(w: T2Warn): Unit = {
    warnings += w
  }

  def defined: Set[TypenameRef] = {
    existing.toSet
  }

  def add(ops: Operation): Unit = {
    ops match {
      case Typer2.Builtin(id) =>
        register(ops, Right(List(index.builtins(id))))

      case single: Typer2.DefineType =>
        val dindex = if (single.main.source == index.defn.id) {
          index
        } else {
          importedIndexes(single.main.source)
        }

        val interpreter = makeInterpreter(dindex).interpreter
//        val refs = requiredTemplates(single.main.defn.defn)
//        if (refs.nonEmpty) {
//          println(refs)
//        }

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


  private def makeInterpreter(dindex: DomainIndex): InterpreterContext = {
    new InterpreterContext(dindex, this, Interpreter.Args(types.toMap, Map.empty))
  }

  ///
  def requiredTemplates(v: RawTypeDef): Seq[RawGenericRef] = {
    v match {
      case t: RawTypeDef.Interface =>
        trefs(t.struct)

      case t: RawTypeDef.DTO =>
        trefs(t.struct)

      case t: RawTypeDef.Alias =>
        collectGenericRefs(List(t.target))

      case t: RawTypeDef.Adt =>
        t.alternatives
          .flatMap {
            case a: Member.TypeRef =>
              collectGenericRefs(List(a.typeId))
            case a: Member.NestedDefn =>
              requiredTemplates(a.nested)
          }

      case n: RawTypeDef.NewType =>
        Seq.empty

      case t: RawTypeDef.Template =>
        requiredTemplates(t.decl)

      case _: RawTypeDef.Enumeration =>
        Seq.empty

      case _: RawTypeDef.Identifier =>
        Seq.empty

      case _: RawTypeDef.ForeignType =>
        Seq.empty

      case i: RawTypeDef.Instance =>
        Seq.empty
    }
  }

  private def trefs(struct: RawStructure): Seq[RawGenericRef] = {
    val allRefs = struct.interfaces ++ struct.concepts ++ struct.removedConcepts ++ struct.fields.map(_.typeId)
    collectGenericRefs(allRefs)
  }

  private def collectGenericRefs(allRefs: List[RawRef]): immutable.Seq[RawGenericRef] = {
    allRefs.collect({ case ref: RawGenericRef => ref })
  }

  ///


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


  def finish(): Either[List[BuilderFail], Typespace2] = {
    for {
      _ <- if (failures.nonEmpty) {
        Left(failures.toList)
      } else {
        Right(())
      }
      allTypes = Ts2Builder.this.types.values.map(_.member).toList
      verifier = new TsVerifier(types.toMap, makeInterpreter(index).resolvers)
      _ <- verifier.validateTypespace(allTypes)
    } yield {
      Typespace2(
        warnings.toList,
        Set.empty,
        this.types.values.toList,
      )
    }
  }

  private def register(ops: Operation, maybeTypes: Either[List[BuilderFail], List[IzType]]): Unit = {
    (for {
      tpe <- maybeTypes
      verifier = new TsVerifier(types.toMap, makeInterpreter(index).resolvers)
      _ <- verifier.prevalidateTypes(tpe)
    } yield {
      tpe
    }) match {
      case Left(value) =>
        fail(ops, value)

      case Right(value) =>
        value.foreach {
          product =>
            types.put(product.id, makeMember(product))
        }

        existing.add(ops.id).discard()
    }
  }


  private def isOwn(id: IzTypeId): Boolean = {
    id match {
      case IzTypeId.BuiltinType(_) =>
        false
      case IzTypeId.UserType(prefix, _) =>
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



