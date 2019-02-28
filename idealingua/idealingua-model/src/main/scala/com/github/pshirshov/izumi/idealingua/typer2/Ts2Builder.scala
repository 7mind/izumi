package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.typer2.ProcessedOp.Exported
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Identified, UnresolvedName}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.IzStructure
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.{IzDomainPath, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.model._

import scala.collection.mutable


sealed trait ProcessedOp {
  def member: IzType
}

object ProcessedOp {

  final case class Exported(member: IzType) extends ProcessedOp

  final case class Imported(member: IzType) extends ProcessedOp

}

case class Typespace2(

                       warnings: List[T2Warn],
                       imports: Set[IzTypeId],
                       types: List[ProcessedOp],
                     )

class Ts2Builder(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex]) {
  private val failed = mutable.HashSet.empty[UnresolvedName]
  private val failures = mutable.ArrayBuffer.empty[InterpretationFail]
  private val existing = mutable.HashSet.empty[UnresolvedName]
  private val types = mutable.HashMap[IzTypeId, ProcessedOp]()
  private val thisPrefix = TypePrefix.UserTLT(IzPackage(index.defn.id.toPackage.map(IzDomainPath)))

  def defined: Set[UnresolvedName] = {
    existing.toSet
  }

  def add(ops: Identified): Unit = {
    ops.defns match {
      case defns if defns.isEmpty =>
        // type requires no ops => builtin
        //existing.add(ops.id).discard()
        register(ops, Right(List(index.builtins(ops.id))))

      case single :: Nil =>
        val dindex = if (single.source == index.defn.id) {
          index
        } else {
          importedIndexes(single.source)
        }
        val interpreter = new Interpreter(dindex, types.toMap)

        val product = single.defn match {
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

          case c: RawTopLevelDefn.TLDNewtype =>
            interpreter.cloneType(c.v)

          case RawTopLevelDefn.TLDForeignType(v) =>
            interpreter.makeForeign(v).asList

          case RawTopLevelDefn.TLDDeclared(v) =>
            Left(List(SingleDeclaredType(v)))
        }
        register(ops, product)

      case o =>
        println(s"Unhandled case: ${o.size} defs")
    }
  }

  def fail(ops: Identified, failures: List[InterpretationFail]): Unit = {
    if (ops.depends.exists(failed.contains)) {
      // dependency has failed already, fine to skip
    } else {
      failed.add(ops.id)
      this.failures ++= failures
    }
  }


  def finish(): Either[List[InterpretationFail], Typespace2] = {
    for {
      _ <- if (failures.nonEmpty) {
        Left(failures.toList)
      } else {
        Right(())
      }
      allTypes = Ts2Builder.this.types.values.collect({ case Exported(member) => member }).toList
      _ <- validateAll(allTypes, postValidate)
    } yield {
      Typespace2(
        List.empty,
        Set.empty,
        this.types.values.toList,
      )
    }
  }

  private def register(ops: Identified, maybeTypes: Either[List[InterpretationFail], List[IzType]]): Unit = {
    (for {
      tpe <- maybeTypes
      _ <- validateAll(tpe, preValidate)
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

  private def validateAll(allTypes: List[IzType], validator: IzType => Either[List[InterpretationFail], Unit]): Either[List[InterpretationFail], Unit] = {
    val bad = allTypes
      .map(validator)
      .collect({ case Left(l) => l })
      .flatten

    if (bad.nonEmpty) {
      Left(bad)
    } else {
      Right(())
    }
  }


  private def postValidate(tpe: IzType): Either[List[InterpretationFail], Unit] = {
    tpe match {
      case structure: IzStructure =>
        structure.fields.foreach {
          f =>
            import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
            f.defined.map(_.as).foreach(parent => assert(isSubtype(f.tpe, parent), s"${f.tpe} ?? $parent\n\n${f.defined.niceList()}\n\n"))
        }
        Right(())
      case o =>
        Right(())
      //      case generic: IzType.Generic =>
      //      case builtinType: IzType.BuiltinType =>
      //      case IzType.IzAlias(id, source, meta) =>
      //      case IzType.Identifier(id, fields, meta) =>
      //      case IzType.Enum(id, members, meta) =>
      //      case foreign: IzType.Foreign =>
      //      case IzType.Adt(id, members, meta) =>
    }
  }

  private def preValidate(tpe: IzType): Either[List[InterpretationFail], Unit] = {
    // TODO: verify
    // don't forget: we don't have ALL the definitions here yet
    Quirks.discard(tpe)
    Right(())
  }

  private def isSubtype(child: IzTypeReference, parent: IzTypeReference): Boolean = {
    (child == parent) || {
      (child, parent) match {
        case (IzTypeReference.Scalar(childId), IzTypeReference.Scalar(parentId)) =>
          (types(childId).member, types(parentId).member) match {
            case (c: IzStructure, p: IzStructure) =>
              c.allParents.contains(p.id)
            case _ =>
              false
          }

        case _ =>
          false // all generics are non-covariant
      }
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


