package izumi.distage.docker.model

import izumi.distage.docker.model.Docker.UnmappedPorts

sealed abstract class DockerException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
  def this(message: String) = this(message, null)
}

case class DockerFailureException(message: String, explanation: DockerFailureCause, cause: Throwable) extends DockerException(message)
object DockerFailureException {
  def apply(message: String, explanation: DockerFailureCause): DockerFailureException = DockerFailureException(message, explanation, null)
}

case class DockerTimeoutException(message: String) extends DockerException(message)

sealed trait DockerFailureCause

object DockerFailureCause {
  case class Terminated(state: Docker.ContainerState) extends DockerFailureCause
  case class Throwed(cause: Throwable) extends DockerFailureCause
  case class MissingPorts(ports: UnmappedPorts) extends DockerFailureCause
  case object Bug extends DockerFailureCause
}