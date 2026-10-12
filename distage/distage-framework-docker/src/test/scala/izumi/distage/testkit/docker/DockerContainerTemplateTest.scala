package izumi.distage.testkit.docker

import distage.SafeType
import izumi.distage.docker.bundled.*
import izumi.distage.docker.impl.ContainerResource
import izumi.distage.docker.model.Docker.DockerPort
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

object CustomPostgres extends PostgresDockerTemplateDef {
  override def version: String = "15"
  override def postgresUser: String = "myuser"
  override def postgresPassword: String = "mypass"
  override def postgresDb: String = "mydb"
}

object CustomCassandra extends CassandraDockerTemplateDef {
  override def version: String = "4.1"
}

object CustomDynamo extends DynamoDockerTemplateDef {
  override def version: String = "2.4.0"
}

object CustomElasticMQ extends ElasticMQDockerTemplateDef {
  override def version: String = "1.4.0"
}

object CustomZookeeper extends ZookeeperDockerTemplateDef {
  override def version: String = "3.9"
}

object CustomKafka extends KafkaDockerTemplateDef {
  override def version: String = "2.13-2.8.1"
}

object CustomKafkaKRaft extends KafkaKRaftDockerTemplateDef {
  override def version: String = "3.8.0"
}

final class DockerContainerTemplateTest extends AnyWordSpec {

  "Container templates" should {

    "produce customized PostgreSQL config" in {
      val cfg = CustomPostgres.config
      assert(cfg.image == "docker/library/postgres:15")
      assert(cfg.env.env(Map.empty)("POSTGRES_USER") == "myuser")
      assert(cfg.env.env(Map.empty)("POSTGRES_PASSWORD") == "mypass")
      assert(cfg.env.env(Map.empty)("POSTGRES_DB") == "mydb")
      assert(cfg.ports == Seq(DockerPort.TCP(5432)))
    }

    "produce customized Cassandra config" in {
      val cfg = CustomCassandra.config
      assert(cfg.image == "docker/library/cassandra:4.1")
      assert(cfg.ports == Seq(DockerPort.TCP(9042)))
    }

    "produce customized DynamoDB config" in {
      val cfg = CustomDynamo.config
      assert(cfg.image == "amazon/dynamodb-local:2.4.0")
      assert(cfg.ports == Seq(DockerPort.TCP(8000)))
    }

    "produce customized ElasticMQ config" in {
      val cfg = CustomElasticMQ.config
      assert(cfg.image == "softwaremill/elasticmq-native:1.4.0")
      assert(cfg.ports == Seq(DockerPort.TCP(9324)))
    }

    "produce customized Zookeeper config" in {
      val cfg = CustomZookeeper.config
      assert(cfg.image == "docker/library/zookeeper:3.9")
      assert(cfg.ports == Seq(DockerPort.TCP(2181)))
    }

    "produce customized Kafka config" in {
      val cfg = CustomKafka.config
      assert(cfg.image == "apache/kafka:2.13-2.8.1")
      assert(CustomKafka.image == "apache/kafka")
    }

    "produce customized KafkaKRaft config" in {
      val cfg = CustomKafkaKRaft.config
      assert(cfg.image == "apache/kafka:3.8.0")
      assert(CustomKafkaKRaft.image == "apache/kafka")
    }

    "have unique Tag types per template instance" in {
      val pgType = SafeType.get[CustomPostgres.Tag]
      val defaultPgType = SafeType.get[PostgresDocker.Tag]
      assert(pgType != defaultPgType, "Custom and default Postgres should have different Tag types")

      val csType = SafeType.get[CustomCassandra.Tag]
      val defaultCsType = SafeType.get[CassandraDocker.Tag]
      assert(csType != defaultCsType, "Custom and default Cassandra should have different Tag types")
    }

    "produce correct return type from make[F]" in {
      assert(CustomPostgres.make[Identity].get.ret == SafeType.get[ContainerResource[Identity, CustomPostgres.Tag]])
      assert(CustomCassandra.make[Identity].get.ret == SafeType.get[ContainerResource[Identity, CustomCassandra.Tag]])
      assert(CustomDynamo.make[Identity].get.ret == SafeType.get[ContainerResource[Identity, CustomDynamo.Tag]])
      assert(CustomElasticMQ.make[Identity].get.ret == SafeType.get[ContainerResource[Identity, CustomElasticMQ.Tag]])
      assert(CustomZookeeper.make[Identity].get.ret == SafeType.get[ContainerResource[Identity, CustomZookeeper.Tag]])
      assert(CustomKafka.make[Identity].get.ret == SafeType.get[ContainerResource[Identity, CustomKafka.Tag]])
      assert(CustomKafkaKRaft.make[Identity].get.ret == SafeType.get[ContainerResource[Identity, CustomKafkaKRaft.Tag]])
    }

    "preserve default configs when not overriding" in {
      assert(PostgresDocker.config.image == "docker/library/postgres:17")
      assert(PostgresDocker.postgresDb == "postgres")
      assert(CassandraDocker.config.image == "docker/library/cassandra:5.0")
      assert(DynamoDocker.config.image == "amazon/dynamodb-local:3.3.0")
      assert(ElasticMQDocker.config.image == "softwaremill/elasticmq-native:1.6.14")
      assert(ZookeeperDocker.config.image == "docker/library/zookeeper:3.9")
      assert(KafkaDocker.config.image == "apache/kafka:3.9.0")
      assert(KafkaKRaftDocker.config.image == "apache/kafka:4.2.0")
    }
  }
}
