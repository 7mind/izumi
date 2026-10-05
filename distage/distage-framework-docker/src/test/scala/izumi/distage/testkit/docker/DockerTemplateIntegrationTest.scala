package izumi.distage.testkit.docker

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import distage.ModuleDef
import izumi.distage.docker.ContainerNetworkDef
import izumi.distage.docker.DockerContainer
import izumi.distage.docker.bundled.*
import izumi.distage.docker.impl.{ContainerResource, DockerClientWrapper}
import izumi.distage.docker.model.Docker.DockerReusePolicy
import izumi.distage.docker.modules.DockerSupportModule
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.Spec2
import izumi.functional.bio.WeakTemporal2
import zio.{IO, Task, ZIO}

import java.io.Closeable
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration.*

object DockerTemplateIntegrationTest {

  def fetchLogs(client: DockerClientWrapper[Task], container: DockerContainer[?]): Task[String] = ZIO.attempt {
    val sb = new StringBuilder
    val latch = new CountDownLatch(1)
    val callback = new ResultCallback[Frame] {
      override def onStart(closeable: Closeable): Unit = ()
      override def onNext(frame: Frame): Unit = sb.append(new String(frame.getPayload))
      override def onError(t: Throwable): Unit = { sb.append(s"[log error: ${t.getMessage}]"); latch.countDown() }
      override def onComplete(): Unit = latch.countDown()
      override def close(): Unit = ()
    }
    client.rawClient
      .logContainerCmd(container.id.name)
      .withStdOut(true)
      .withStdErr(true)
      .withTail(200)
      .exec(callback)
    latch.await(10, TimeUnit.SECONDS)
    sb.toString
  }


  object TestPostgres extends PostgresDockerTemplateDef {
    override def version: String = "16"
    override def postgresUser: String = "testuser"
    override def postgresPassword: String = "testpass"
    override def postgresDb: String = "testdb"

    override def config: Config = super.config.copy(reuse = DockerReusePolicy.ReuseDisabled)
  }

  object TestDynamo extends DynamoDockerTemplateDef {
    override def config: Config = super.config.copy(reuse = DockerReusePolicy.ReuseDisabled)
  }

  object TestElasticMQ extends ElasticMQDockerTemplateDef {
    override def config: Config = super.config.copy(reuse = DockerReusePolicy.ReuseDisabled)
  }

  object TestKafkaKRaft extends KafkaKRaftDockerTemplateDef {
    override def config: Config = super.config.copy(reuse = DockerReusePolicy.ReuseDisabled)
  }

  object TestZookeeper extends ZookeeperDockerTemplateDef {
    override def config: Config = super.config.copy(reuse = DockerReusePolicy.ReuseDisabled)
  }

  object TestKafka extends KafkaDockerTemplateDef {
    override def config: Config = super.config.copy(reuse = DockerReusePolicy.ReuseDisabled)
  }

  val testModule: ModuleDef = new ModuleDef {
    include(DockerSupportModule[Task])

    make[ContainerResource[Task, TestPostgres.Tag]].from(TestPostgres.make[Task])
    make[ContainerResource[Task, TestDynamo.Tag]].from(TestDynamo.make[Task])
    make[ContainerResource[Task, TestElasticMQ.Tag]].from(TestElasticMQ.make[Task])
    make[ContainerResource[Task, TestKafkaKRaft.Tag]].from(TestKafkaKRaft.make[Task])

    make[ContainerNetworkDef.ContainerNetwork[KafkaZookeeperNetwork.Tag]].fromResource {
      KafkaZookeeperNetwork.make[Task]
    }
    make[TestZookeeper.Container].fromResource {
      TestZookeeper.make[Task].connectToNetwork(KafkaZookeeperNetwork)
    }
    make[TestKafka.Container].fromResource {
      TestKafka.make[Task]
        .connectToNetwork(KafkaZookeeperNetwork)
        .dependOnContainerPorts(TestZookeeper)(2181 -> "KAFKA_ZOOKEEPER_CONNECT")
    }
  }
}

final class DockerTemplateIntegrationTest extends Spec2[IO] {
  import DockerTemplateIntegrationTest.*

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = testModule
  )

  "Template-based containers" should {

    "launch and destroy PostgreSQL from template" in {
      (pgResource: ContainerResource[Task, TestPostgres.Tag], client: DockerClientWrapper[Task]) =>
        pgResource.use {
          container =>
            // .use block only runs after the health check (postgreSqlProtocolCheck) succeeds;
            // additionally assert that PG logged "database system is ready to accept connections"
            val pollLogs: Task[Option[String]] = fetchLogs(client, container).flatMap {
              logs =>
                val lower = logs.toLowerCase
                if (lower.contains("fatal") || lower.contains("panic") || lower.contains("exception in thread")) {
                  ZIO.fail(new AssertionError(s"Postgres container logs contain fatal error:\n$logs"))
                } else if (logs.contains("database system is ready to accept connections")) {
                  ZIO.succeed(Some(logs))
                } else {
                  ZIO.succeed(None)
                }
            }
            for {
              _ <- ZIO.attempt(assert(container.availablePorts.firstOption(TestPostgres.primaryPort).nonEmpty))
              _ <- pollLogs.repeatUntil(
                new AssertionError("Postgres never logged 'database system is ready to accept connections'"),
                sleep = 3.seconds,
                maxAttempts = 20,
              )
            } yield ()
        }
    }

    "launch and destroy DynamoDB from template" in {
      (dynamoResource: ContainerResource[Task, TestDynamo.Tag]) =>
        dynamoResource.use {
          container =>
            ZIO.attempt {
              assert(container.availablePorts.firstOption(TestDynamo.primaryPort).nonEmpty)
            }
        }
    }

    "launch and destroy ElasticMQ from template" in {
      (mqResource: ContainerResource[Task, TestElasticMQ.Tag]) =>
        mqResource.use {
          container =>
            ZIO.attempt {
              assert(container.availablePorts.firstOption(TestElasticMQ.primaryPort).nonEmpty)
            }
        }
    }

    "launch and destroy Kafka (KRaft) from template" in {
      (kafkaResource: ContainerResource[Task, TestKafkaKRaft.Tag], client: DockerClientWrapper[Task]) =>
        kafkaResource.use {
          container =>
            val pollLogs: Task[Option[String]] = fetchLogs(client, container).flatMap {
              logs =>
                val lower = logs.toLowerCase
                if (lower.contains("fatal") || lower.contains("exception in thread")) {
                  // fail fast on divergence
                  ZIO.fail(new AssertionError(s"Kafka container logs contain fatal error:\n$logs"))
                } else if (logs.contains("Kafka Server started")) {
                  ZIO.succeed(Some(logs))
                } else {
                  ZIO.succeed(None)
                }
            }
            for {
              _ <- ZIO.attempt(assert(container.availablePorts.firstOption(TestKafkaKRaft.primaryPort).nonEmpty))
              logs <- pollLogs.repeatUntil(
                new AssertionError("Kafka never logged 'Kafka Server started' within the attempt budget"),
                sleep = 3.seconds,
                maxAttempts = 20,
              )
              _ = println(s"===== Kafka KRaft container logs =====\n$logs\n===== end logs =====")
            } yield ()
        }
    }

    "launch and destroy Kafka (Zookeeper-based) from template" in {
      (kafka: TestKafka.Container) =>
        ZIO.attempt {
          assert(kafka.availablePorts.firstOption(TestKafka.primaryPort).nonEmpty)
        }
    }
  }
}
