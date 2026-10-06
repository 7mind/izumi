package izumi.distage.sbt

import sbt.{TestDefinition, Tests}
import sbt.io.GlobFilter
import sbt.util.Logger

private[sbt] final class HostReportedSelection(val inherited: Seq[String] => Seq[String => Boolean], reason: (String, Seq[String]) => HostSelectionPolicy.Reason, log: Logger)
  extends (Seq[String] => Seq[String => Boolean]) {
  override def apply(arguments: Seq[String]): Seq[String => Boolean] = {
    HostSelectionPolicy.observeFilters(inherited(arguments), name => {
      HostSelectionPolicy.exclude(name, reason(name, arguments), log)
    })
  }
}

private[sbt] object HostSelectionPolicy {
  sealed abstract class Reason(val value: String)
  object Reason {
    case object UserRequest extends Reason("user-request")
    case object UserConfiguration extends Reason("user-configuration")
    case object CachedSuccess extends Reason("cached-success")
    case object InheritedFilter extends Reason("inherited-filter")
  }

  def request(arguments: Seq[String]): String => Boolean = {
    val (excluded, included) = arguments.takeWhile(_ != "--").partition(_.startsWith("-"))
    val includes = included.map(value => GlobFilter(value.replace("...", "**")))
    val excludes = excluded.map(value => GlobFilter(value.substring(1).replace("...", "**")))
    name => (includes.isEmpty || includes.exists(_.accept(name))) && !excludes.exists(_.accept(name))
  }

  def exclude(name: String, reason: Reason, log: Logger): Unit = {
    log.info("DISTAGE_SELECTION_DECISION suite=" + name + " decision=exclude reason=" + reason.value)
  }

  def observeFilters(filters: Seq[String => Boolean], excluded: String => Unit): Seq[String => Boolean] = {
    val includedNames = scala.collection.mutable.HashSet.empty[HostSuiteName]
    filters.zipWithIndex.map { case (filter, index) => name =>
      val included = filter(name)
      includedNames.synchronized {
        if (included) includedNames += HostSuiteName(name)
        // SBT visits ordered filters in order; exclusion requires every filter to reject.
        if (index == filters.size - 1 && !includedNames.contains(HostSuiteName(name))) excluded(name)
      }
      included
    }
  }

  def configured(execution: Tests.Execution, definitions: Seq[TestDefinition], log: Logger): Tests.Execution = {
    def observe(filter: String => Boolean): String => Boolean = name => {
      val included = filter(name)
      if (!included) exclude(name, Reason.UserConfiguration, log)
      included
    }
    val names = definitions.map(_.name).toSet
    val options = execution.options.map {
      case option @ Tests.Exclude(excluded) =>
        names.intersect(excluded.toSet).toVector.sorted.foreach(name => exclude(name, Reason.UserConfiguration, log))
        option
      case Tests.Filter(filter) => Tests.Filter(observe(filter))
      case Tests.Filters(filters) => Tests.Filters(observeFilters(filters, name => exclude(name, Reason.UserConfiguration, log)))
      case other => other
    }
    execution.copy(options = options)
  }
}
