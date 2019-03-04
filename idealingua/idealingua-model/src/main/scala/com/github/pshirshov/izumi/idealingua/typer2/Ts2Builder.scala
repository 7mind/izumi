package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Operation, UnresolvedName}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.{IzDomainPath, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp
import com.github.pshirshov.izumi.idealingua.typer2.model.Typespace2.ProcessedOp.Exported
import com.github.pshirshov.izumi.idealingua.typer2.model._

import scala.collection.mutable


class Ts2Builder(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex]) extends WarnLogger {
  private val failed = mutable.HashSet.empty[UnresolvedName]
  private val failures = mutable.ArrayBuffer.empty[BuilderFail]
  private val warnings = mutable.ArrayBuffer.empty[T2Warn]
  private val existing = mutable.HashSet.empty[UnresolvedName]
  private val types = mutable.HashMap[IzTypeId, ProcessedOp]()
  private val thisPrefix = TypePrefix.UserTLT(IzPackage(index.defn.id.toPackage.map(IzDomainPath)))


  override def log(w: T2Warn): Unit = {
    warnings += w
  }

  def defined: Set[UnresolvedName] = {
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
        val interpreter = new Interpreter(dindex, types.toMap, this)

        val product = single.main.defn match {
          case RawTopLevelDefn.TLDBaseType(v) =>
            v match {
              case i: RawTypeDef.Interface =>
                interpreter.makeInterface(i).asList

              case d: RawTypeDef.DTO =>
                interpreter.makeDto(d).asList

              case a: RawTypeDef.Alias =>
                interpreter.makeAlias(a).asList

              case e: RawTypeDef.Enumeration =>
                interpreter.makeEnum(e).asList

              case i: RawTypeDef.Identifier =>
                interpreter.makeIdentifier(i).asList

              case a: RawTypeDef.Adt =>
                interpreter.makeAdt(a)
            }

          case t: RawTopLevelDefn.TLDTemplate =>
            interpreter.makeTemplate(t.v)

          case t: RawTopLevelDefn.TLDInstance =>
            interpreter.makeInstance(t.v)

          case c: RawTopLevelDefn.TLDNewtype =>
            interpreter.cloneType(c.v)

          case RawTopLevelDefn.TLDForeignType(v) =>
            interpreter.makeForeign(v).asList
        }
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
      allTypes = Ts2Builder.this.types.values.collect({ case Exported(member) => member }).toList
      verifier = new TsVerifier(types.toMap)
      _ <- verifier.validateAll(allTypes, verifier.postValidate)
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
      verifier = new TsVerifier(types.toMap)
      _ <- verifier.validateAll(tpe, verifier.preValidate)
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



