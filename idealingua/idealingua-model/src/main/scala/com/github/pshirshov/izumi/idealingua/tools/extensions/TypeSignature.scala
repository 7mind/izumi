//package com.github.pshirshov.izumi.idealingua.tools.extensions
//
//import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId
//import com.github.pshirshov.izumi.idealingua.model.common._
//import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod._
//import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
//import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._
//import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
//import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
//
//import scala.util.hashing.MurmurHash3
//
//class TypeSignature(typespace: Typespace) {
//  def signature(id: TypeId): Int = {
//    signature(id, Set.empty)
//  }
//
//  def signature(id: ServiceId): Int = {
//    val service = typespace(id)
//    MurmurHash3.orderedHash(simpleSignature(service.id) +: service.methods.flatMap {
//      case r: DeprecatedRPCMethod =>
//        Seq(MurmurHash3.stringHash(r.name), MurmurHash3.orderedHash(r.signature.asList.map(signature)))
//    })
//  }
//
//
//  protected def signature(id: TypeId, seen: Set[TypeId]): Int = {
//    id match {
//      case b: Primitive =>
//        simpleSignature(b)
//
//      case b: Generic =>
//        val argSig = b.args.map {
//          case v if seen.contains(v) => simpleSignature(v)
//          case argid => signature(argid, seen + argid)
//        }
//        MurmurHash3.orderedHash(simpleSignature(b) +: argSig)
//
//
//      case _ =>
//        val primitiveSignature = explode(typespace.apply(id))
//        val numbers = primitiveSignature.flatMap {
//          tf =>
//            Seq(simpleSignature(tf.typeId), signature(tf.typeId, seen))
//        }
//        MurmurHash3.orderedHash(numbers)
//    }
//  }
//
//  protected def simpleSignature(id: TypeId): Int = {
//    MurmurHash3.orderedHash((id.pkg :+ id.name).map(MurmurHash3.stringHash))
//  }
//
//  protected def simpleSignature(id: ServiceId): Int = {
//    MurmurHash3.orderedHash((id.pkg :+ id.name).map(MurmurHash3.stringHash))
//  }
//
//  protected def explode(defn: TypeDef): List[TrivialField] = {
//    defn match {
//      case t: Interface =>
//        t.struct.superclasses.all.flatMap(i => explode(typespace(i)))
//          t.struct.fields.flatMap(explode)
//
//      case t: DTO =>
//        t.struct.superclasses.all.flatMap(i => explode(typespace(i)))
//
//      case t: Adt =>
//        t.alternatives.map(_.typeId).map(typespace.apply).flatMap(explode)
//
//      case t: Identifier =>
//        t.fields.flatMap(explode)
//
//      case _: Alias =>
//        List()
//
//      case _: Enumeration =>
//        List()
//    }
//  }
//
//  protected def explode(defn: Field): List[TrivialField] = {
//    defn.typeId match {
//      case t: Builtin =>
//        List(TrivialField(t, defn.name))
//      case t =>
//        explode(typespace.apply(t))
//    }
//  }
//
//  protected def explode(defn: PrimitiveField): List[TrivialField] = {
//    defn.typeId match {
//      case t: Primitive =>
//        List(TrivialField(t, defn.name))
//    }
//  }
//
//}
