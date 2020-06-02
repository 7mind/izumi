package izumi.distage.docker
import izumi.distage.docker.Docker.DockerPort
import izumi.distage.docker.healthcheck.ContainerHealthCheck.HealthCheckResult
import izumi.distage.docker.healthcheck.chain.{HttpProtocolCheck, PostgreSqlProtocolCheck}

package object healthcheck {
  def dontCheckPorts[T]: ContainerHealthCheck[T] = (_, _) => HealthCheckResult.Ignored
  def checkTCPOnly[T]: ContainerHealthCheck[T] = new TCPContainerHealthCheck[T]
  def withPostgresProtocolCheck[T]: ContainerHealthCheck[T] = checkTCPOnly ++ new PostgreSqlProtocolCheck[T]
  def withHttpProtocolCheck[T](port: DockerPort): ContainerHealthCheck[T] = checkTCPOnly ++ new HttpProtocolCheck[T](port)
}
