package izumi.distage.testkit.protocol

import java.nio.file.{Files, Path, Paths}

final case class ForkReceiptInvocation(arguments: Vector[String], remoteArguments: Vector[String], hostDirectory: Option[Path], eventDirectory: Option[Path], forked: Boolean, commandCompletion: Boolean) {
  def forwardedRemoteArguments: Vector[String] = hostDirectory match {
    case Some(directory) if !forked => remoteArguments ++ Vector(ForkReceiptArguments.ForkDirectoryOption, directory.toString)
    case _ => remoteArguments
  }
}

object ForkReceiptArguments {
  final val HostDirectoryOption = "--distage-host-receipts"
  final val ForkDirectoryOption = "--distage-fork-receipts"
  final val CommandCompletionOption = "--distage-command-completion"
  final val EventDirectoryOption = "--distage-events"

  def parse(arguments: Vector[String], remoteArguments: Vector[String]): ForkReceiptInvocation = {
    require(!arguments.contains(ForkDirectoryOption) && !remoteArguments.contains(HostDirectoryOption), "Fork receipt option is in the wrong argument channel")
    require(!remoteArguments.contains(CommandCompletionOption), "Command completion option is in the wrong argument channel")
    require(!remoteArguments.contains(EventDirectoryOption), "Event directory option is in the wrong argument channel")
    require(arguments.count(_ == CommandCompletionOption) <= 1, "Duplicate command completion option")
    val commandCompletion = arguments.contains(CommandCompletionOption)
    val (hostValues, host) = extract(arguments.filterNot(_ == CommandCompletionOption), HostDirectoryOption)
    val (values, events) = extract(hostValues, EventDirectoryOption)
    require(!commandCompletion || host.nonEmpty, "Command completion has no host ownership")
    val (_, target) = extract(remoteArguments, ForkDirectoryOption)
    require(target.isEmpty || target == host, "Fork receipt activation differs from host ownership")
    ForkReceiptInvocation(values, remoteArguments, host, events, target.nonEmpty, commandCompletion)
  }

  private def extract(arguments: Vector[String], option: String): (Vector[String], Option[Path]) = {
    val occurrences = arguments.zipWithIndex.collect { case (value, index) if value == option => index }
    require(occurrences.size <= 1, "Duplicate protocol directory option: " + option)
    occurrences.headOption match {
      case None => arguments -> None
      case Some(index) =>
        require(index + 1 < arguments.size, "Missing protocol directory: " + option)
        val directory = Paths.get(arguments(index + 1))
        require(directory.isAbsolute && Files.isDirectory(directory), "Protocol directory must be an existing absolute path: " + option)
        (arguments.take(index) ++ arguments.drop(index + 2)) -> Some(directory)
    }
  }
}
