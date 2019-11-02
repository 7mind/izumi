package izumi.idealingua.model.typespace

import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common._
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import izumi.idealingua.model.il.ast.typed._

import scala.collection.mutable


class TypespaceImpl(val domain: DomainDefinition) extends Typespace with TypeResolver {
  lazy val types: TypeCollection = new TypeCollection(this)

  protected[typespace] lazy val transitivelyReferenced: Map[DomainId, Typespace] = {
    val allReferences = mutable.HashSet.empty[DomainDefinition]
    collectReferenced(domain, allReferences)
    allReferences.map(d => d.id -> new TypespaceImpl(d)).toMap // 2.13 compat
  }

  private def collectReferenced(d: DomainDefinition, all: mutable.HashSet[DomainDefinition]): Unit = {
    val r = d.referenced.values.map(_.perform()).toSet
    val newR = r.diff(all)
    all ++= newR
    newR.foreach(rd => collectReferenced(rd, all))
  }

  private lazy val index: CMap[TypeId, TypeDef] = types.index

  override val resolver: TypeResolver = this

  override lazy val tools: TypespaceTools = new TypespaceToolsImpl(this)

  override lazy val inheritance: InheritanceQueries = new InheritanceQueriesImpl(this)

  override lazy val structure: StructuralQueries = new StructuralQueriesImpl(this)

  def apply(id: ServiceId): Service = {
    types.services(id)
  }

  def dealias(t: TypeId): TypeId = {
    t match {
      case a: AliasId =>
        dealias(apply(a).asInstanceOf[Alias].target)

      case o =>
        o
    }
  }

  def apply(id: TypeId): TypeDef = {
    if (index.underlying.contains(id)) {
      index.fetch(id)
    } else {
      transitivelyReferenced.get(id.path.domain) match {
        case Some(d) =>
          d.apply(id)
        case None =>
          throw new IDLException(s"Foreign lookup from domain ${domain.id} failed: can't get ${id.path.domain} reference while looking for $id")
      }
    }
  }

}



