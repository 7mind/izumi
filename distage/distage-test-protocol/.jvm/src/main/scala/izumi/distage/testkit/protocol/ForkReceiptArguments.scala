package izumi.distage.testkit.protocol

import java.nio.file.{Files, Path, Paths}

final case class ForkReceiptInvocation(arguments: Vector[String], remoteArguments: Vector[String], hostDirectory: Option[Path], forked: Boolean) {
  def forwardedRemoteArguments: Vector[String] = hostDirectory match {
    case Some(directory) if !forked => remoteArguments ++ Vector(ForkReceiptArguments.ForkDirectoryOption, directory.toString)
    case _ => remoteArguments
  }
}

object ForkReceiptArguments {
  final val HostDirectoryOption = "--distage-host-receipts"
  final val ForkDirectoryOption = "--distage-fork-receipts"

  def parse(arguments: Vector[String], remoteArguments: Vector[String]): ForkReceiptInvocation = {
    require(!arguments.contains(ForkDirectoryOption) && !remoteArguments.contains(HostDirectoryOption), "Fork receipt option is in the wrong argument channel")
    val (values, host) = extract(arguments, HostDirectoryOption)
    val (_, target) = extract(remoteArguments, ForkDirectoryOption)
    require(target.isEmpty || target == host, "Fork receipt activation differs from host ownership")
    ForkReceiptInvocation(values, remoteArguments, host, target.nonEmpty)
  }

  private def extract(arguments: Vector[String], option: String): (Vector[String], Option[Path]) = {
    val occurrences = arguments.zipWithIndex.collect { case (value, index) if value == option => index }
    require(occurrences.size <= 1, "Duplicate fork receipt option: " + option)
    occurrences.headOption match {
      case None => arguments -> None
      case Some(index) =>
        require(index + 1 < arguments.size, "Missing fork receipt directory: " + option)
        val directory = Paths.get(arguments(index + 1))
        require(directory.isAbsolute && Files.isDirectory(directory), "Fork receipt directory must be an existing absolute path")
        (arguments.take(index) ++ arguments.drop(index + 2)) -> Some(directory)
    }
  }
}
