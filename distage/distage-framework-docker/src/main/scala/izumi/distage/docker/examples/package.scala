package izumi.distage.docker

import distage.TagK

import scala.annotation.nowarn

package object examples {
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val CassandraDocker: bundled.CassandraDocker.type = bundled.CassandraDocker
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val DynamoDocker: bundled.DynamoDocker.type = bundled.DynamoDocker
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val ElasticMQDocker: bundled.ElasticMQDocker.type = bundled.ElasticMQDocker
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val KafkaDocker: bundled.KafkaDocker.type = bundled.KafkaDocker
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val PostgresDocker: bundled.PostgresDocker.type = bundled.PostgresDocker
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val ZookeeperDocker: bundled.ZookeeperDocker.type = bundled.ZookeeperDocker
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val KafkaZookeeperNetwork: bundled.KafkaZookeeperNetwork.type = bundled.KafkaZookeeperNetwork

  @nowarn("msg=package object")
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  class CassandraDockerModule[F[_]: TagK] extends bundled.CassandraDockerModule[F]
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val CassandraDockerModule: bundled.CassandraDockerModule.type = bundled.CassandraDockerModule

  @nowarn("msg=package object")
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  class DynamoDockerModule[F[_]: TagK] extends bundled.DynamoDockerModule[F]
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val DynamoDockerModule: bundled.DynamoDockerModule.type = bundled.DynamoDockerModule

  @nowarn("msg=package object")
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  class ElasticMQDockerModule[F[_]: TagK] extends bundled.ElasticMQDockerModule[F]
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val ElasticMQDockerModule: bundled.ElasticMQDockerModule.type = bundled.ElasticMQDockerModule

  @nowarn("msg=package object")
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  class KafkaDockerModule[F[_]: TagK] extends bundled.KafkaDockerModule[F]
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val KafkaDockerModule: bundled.KafkaDockerModule.type = bundled.KafkaDockerModule

  @nowarn("msg=package object")
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  class PostgresDockerModule[F[_]: TagK] extends bundled.PostgresDockerModule[F]
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val PostgresDockerModule: bundled.PostgresDockerModule.type = bundled.PostgresDockerModule

  @nowarn("msg=package object")
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  class ZookeeperDockerModule[F[_]: TagK] extends bundled.ZookeeperDockerModule[F]
  @deprecated(
    "All docker container definitions from `izumi.docker.examples` package have been moved to `izumi.docker.bundled` package",
    "old package will be deleted in 0.11.2",
  )
  lazy val ZookeeperDockerModule: bundled.ZookeeperDockerModule.type = bundled.ZookeeperDockerModule
}
