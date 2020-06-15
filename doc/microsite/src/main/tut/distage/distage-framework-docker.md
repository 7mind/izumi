distage-framework-docker
========================

@@toc { depth=2 }

### Key Features

- Provides Docker containers as resources
- Reasonable defaults with flexible configuration
- Excellent for providing Docker implementations of services for integration tests

### Dependencies

Add the `distage-framework-docker` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-framework-docker" % "$izumi.version$"
```

@@@


### Overview

Usage of `distage-framework-docker` generally follows these steps:

1. Create @scaladoc[`ContainerDef`](izumi.distage.docker.ContainerDef)s for the containers the
   application requires
2. Create a module that declares the container component
3. Include the @scaladoc[`DockerSupportModule`](izumi.distage.docker.modules.DockerSupportModule) in
   the application's modules

#### Setup

Required imports:

```scala mdoc:silent
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK
```

#### 1. Create a `ContainerDef`

The @scaladoc[`ContainerDef`](izumi.distage.docker.ContainerDef) is a trait used to define a Docker
container. Extend this trait and provide an implementation of the `config` method.

The required parameters for `Config` are:

- `image` - the docker image to use
- `ports` - ports to map on the docker container

See @scaladoc[`Docker.ContainerConfig[Tag]`](izumi.distage.docker.Docker$$ContainerConfig) for
additional parameters.

Example [postgres](https://hub.docker.com/_/postgres/) container definition:

```scala mdoc:silent
object PostgresDocker extends ContainerDef {
  val primaryPort: Docker.DockerPort = Docker.DockerPort.TCP(5432)

  override def config: Config = {
    Config(
      image = "library/postgres:12.2",
      ports = Seq(primaryPort),
      env = Map("POSTGRES_PASSWORD" -> "postgres"),
    )
  }
}
```

#### 2. Declare Container Components

To use this container, a module that declares this component is required:

Use @scaladoc[`make`](izumi.distage.docker.ContainerDef#make) for binding in a `ModuleDef`:

```scala mdoc:silent
class PostgresDockerModule[F[_]: TagK] extends ModuleDef {
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[F]
  }
}
```


#### 3. Include the `DockerSupportModule`

Include the `izumi.distage.docker.modules.DockerSupportModule` module in the application
modules. This module contains required component declarations and initializes the
`Docker.ClientConfig`.

```scala mdoc:silent
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import distage.Injector
import izumi.distage.config.AppConfigModule
import izumi.distage.docker.modules.DockerSupportModule
import izumi.distage.effect.modules.CatsDIEffectModule
import izumi.logstage.api.routing.StaticLogRouter
import izumi.logstage.distage.LogIOModule

val distageFrameworkModules = new ModuleDef {
  // required for docker
  include(new DockerSupportModule[IO])

  // standard distage framework modules
  include(AppConfigModule(ConfigFactory.defaultApplication))
  include(new CatsDIEffectModule {})
  include(new LogIOModule[IO](StaticLogRouter.instance, false))
}
```

The required framework modules plus `PostgresDockerModule` is sufficient to depend on Docker
containers:

```scala mdoc:to-string
def minimalExample = {
  val applicationModules = new ModuleDef {
    include(new PostgresDockerModule[IO])
    include(distageFrameworkModules)
  }

  Injector().produceGetF[IO, PostgresDocker.Container](applicationModules).use { container =>
    val port = container.availablePorts.first(PostgresDocker.primaryPort)
    IO(println(s"postgres is available on port ${port}"))
  }
}

minimalExample.unsafeRunSync()
```

If the `DockerSupportModule` is not included in an application then a get of a Docker container
dependent resource will fail with a `izumi.distage.model.exceptions.ProvisioningException`.

### Config API

The @scaladoc[`DockerProviderExtensions`](izumi.distage.docker.DockerContainer$$DockerProviderExtensions)
provides additional APIs for modiying the container definition.

#### `modifyConfig`

Use
@scaladoc[`modifyConfig`](izumi.distage.docker.DockerContainer$$DockerProviderExtensions#modifyConfig)
to modify the configuration of a container. The modifier is instantiated to a `ProviderMagnet`, which
will summon any additional dependencies.

For example, to change the user of the PostgreSQL container:

```scala mdoc:silent
class PostgresRunAsAdminModule[F[_]: TagK] extends ModuleDef {
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[F].modifyConfig { () => (old: PostgresDocker.Config) =>
      old.copy(user = Option("admin"))
    }
  }
}
```

Suppose `HostPostgresData` is a component provided by the application modules. This path can be
added to the PostgreSQL container's mounts by adding to the additional dependencies of the provider
magnet:

```scala mdoc:silent
case class HostPostgresData(path: String)

class PostgresWithMountsDockerModule[F[_]: TagK] extends ModuleDef {
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[F].modifyConfig {
      (hostPostgresData: HostPostgresData) =>
        (old: PostgresDocker.Config) =>
          val dataMount = Docker.Mount(hostPostgresData.path, "/var/lib/postgresql/data")
          old.copy(mounts = old.mounts :+ dataMount)
    }
  }
}
```

#### `dependOnDocker`

@scaladoc[`dependOnDocker`](izumi.distage.docker.DockerContainer$$DockerProviderExtensions#dependOnDocker)
adds a dependency on a given Docker container. `distage` ensures the requested container is available
before the dependent is provided.

Suppose the system under test is a sync from PostgreSQL to Elasticsearch. One option is to use
`dependOnDocker` to declare the Elasticsearch container depends on the PostgreSQL container:

```scala mdoc:silent

object ElasticSearchDocker extends ContainerDef {
  val ports = Seq(9200, 9300)

  override def config: Config = {
    Config(
      image = "docker.elastic.co/elasticsearch/elasticsearch:7.7.0",
      ports = ports.map(Docker.DockerPort.TCP(_)),
      env = Map("discovery.type" -> "single-node")
    )
  }
}

class ElasticSearchPlusPostgresModule[F[_]: TagK] extends ModuleDef {
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[F]
  }

  make[ElasticSearchDocker.Container].fromResource {
    ElasticSearchDocker.make[F].dependOnDocker(PostgresDocker)
  }
}
```

Another example of dependencies between containers is in the "Docker Container Networks" section later in this document.

### Usage in Integration Tests

A common use case is using Docker containers to provide service implementations for integration test, such as using a PostgreSQL container for verifying an application that uses a PostgreSQL database.
`distage` container resources are easy to integrate as providers.

Consider the example application below. This application is written to depend on a
[doobie](https://tpolecat.github.io/doobie/) `Transactor`, which is constructed from a
`PostgresServerConfig`.

```scala mdoc:silent
import doobie._
import doobie.implicits._
import java.util.Date

class PostgresExampleApp(xa: Transactor[IO]) {
  def plusOne(a: Int): IO[Int] =
    sql"select ${a} + 1".query[Int].unique.transact(xa)

  val run: IO[Unit] = plusOne(1) flatMap { v => IO(println(s"1 + 1 = ${v}.")) }
}

// the postgres configuration used to construct the Transactor
case class PostgresServerConfig(
  host: String,
  port: Int,
  database: String,
  username: String,
  password: String
)

class TransactorFromConfigModule extends ModuleDef {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val contextShift = IO.contextShift(global)
  implicit val timer = IO.timer(global)

  make[Transactor[IO]].from { config: PostgresServerConfig =>
    Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      s"jdbc:postgresql://${config.host}:${config.port}/${config.database}",
      config.username,
      config.password
    )
  }
}

```

Note that the above code is agnostic of environment. Provided a `PostgresServerConfig`, the
`Transactor` needed by `PostgresExampleApp` can be constructed.

An integration test would use a module that provides the `PostgresServerConfig` from a
`PostgresDocker.Container`:

```scala mdoc:silent

class IntegUsingDockerModule extends ModuleDef {
  make[PostgresServerConfig].from {
    container: PostgresDocker.Container => {
      val knownAddress = container.availablePorts.first(PostgresDocker.primaryPort)
      PostgresServerConfig(knownAddress.hostV4,
                           knownAddress.port,
                           "postgres",
                           "postgres",
                           "postgres")
    }
  }

  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[IO]
  }
}
```

Using `distage-testkit` the test would be written like this:

```scala mdoc:silent
import izumi.distage.testkit.scalatest.DistageSpecScalatest
import distage.DIKey

class PostgresExampleAppIntegTest extends DistageSpecScalatest[IO] {

  override def config = super.config.copy(
    moduleOverrides = new ModuleDef {
      include(new TransactorFromConfigModule)
      include(new IntegUsingDockerModule)
      include(distageFrameworkModules)
      make[PostgresExampleApp]
    },
    memoizationRoots = Set(
      DIKey[PostgresServerConfig]
    )
  )
  "distage docker" should {
    "support integ tests using containers" in { app: PostgresExampleApp =>
      app.plusOne(1) map { v: Int => assert(v == 2) }
    }
  }
}
```

Typically, this would be run by the test runner. For completeness, the example can be run directly
using:

```scala mdoc:to-string
def postgresDockerIntegExample = {
  val applicationModules = new ModuleDef {
    include(new TransactorFromConfigModule)
    include(new IntegUsingDockerModule)
    include(distageFrameworkModules)
    make[PostgresExampleApp]
  }

  Injector().produceGetF[IO, PostgresExampleApp](applicationModules).use { app =>
    app.run
  }
}

postgresDockerIntegExample.unsafeRunSync()
```

### Docker Container Environment

The container config (@scaladoc[Docker.ContainerConfig](izumi.distage.docker.Docker$$ContainerConfig))
defines the container environment. Of note are the environment variables, command of entrypoint, and working
directory properties:

- `env: Map[String, String]` - environment variables to setup in container
- `cmd: Seq[String]` - command of entrypoint
- `cwd: Option[String]` - working directory in container

Once defined in a `ContainerDef` the config may be modified by distage modules. See `modifyConfig`
above for one mechanism.

The container ports will also add to the configured environment variables:

- `DISTAGE_PORT_<proto>_<originalPort>` are the host ports allocated for the container


### Docker Metadata

The host ports allocated for the container are also added to the container metadata as labels.
These labels follow the pattern `distage.port.<proto>.<originalPort>`. The value is the integer port
number.

### Docker Container Networks

`distage-framework-docker` can automatically manage [Docker
networks](https://docs.docker.com/engine/reference/commandline/network/).

To connect containers to the same Docker network, use a
@scaladoc[`ContainerNetworkDef`](izumi.distage.docker.ContainerNetworkDef):

1. Create a `ContainerNetworkDef`.
2. Add the network to each container's config.

This will ensure the containers are all connected to the network. Assuming no reuse, distage will
create the required network and add each container to that network.

#### 1. Create a `ContainerNetworkDef`

A minimal @scaladoc[`ContainerNetworkDef`](izumi.distage.docker.ContainerNetworkDef) uses the default
configuration.

```mdoc:silent
object TestClusterNetwork extends ContainerNetworkDef {
  override def config: Config = Config()
}
```

By default this object identifies the network. The associated tag type uniquely identifies this
network within the application. In addition, any created network will have a label with this name in
Docker.

#### 2. Add to Container Config

A container will be connected to all networks in the `networks` of the `config`. The method
@scaladoc[`connectToNetwork`](izumi.distage.docker.DockerContainer$$DockerProviderExtensions#connectToNetwork)
adds a dependency on a network defined by a `ContainerNetworkDef`, as in this example:

```mdoc:silent
class TestClusterNetworkModule[F[_]: TagK] extends ModuleDef {
  make[TestClusterNetwork.Network].fromResource {
    TestClusterNetwork.make[F]
  }
  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[F].connectToNetwork(TestClusterNetwork)
  }
  make[ElasticSearchDocker.Container].fromResource {
    ElasticSearchDocker.make[F].dependOnDocker(PostgresDocker).connectToNetwork(TestClusterNetwork)
  }
}
```

The use of `connectToNetwork` automatically adds a dependency on `TestClusterNetwork.Network` to each
container.

#### Container Network Reuse

Container networks, like containers, are reused by default. If there is an existing network that
matches a definition then that network will be used. This can be disabled by setting the `reuse`
configuration to false:

```mdoc:silent
object AlwaysFreshNetwork extends ContainerNetworkDef {
  override def config: Config = Config(reuse = false)
}
```

For an existing network to be reused, the config and object name at time of creation must be the match the current configuration value and object.

### Docker Client Configuration

The @scaladoc[`Docker.ClientConfig`](izumi.distage.docker.Docker$$ClientConfig) is the configuration
of the Docker client used. Including the module `DockerSupportModule` will provide a
`Docker.ClientConfig`.

There are two primary mechanisms to change the `Docker.ClientConfig` provided by
`DockerSupportModule`:

1. Provide a `docker` configuration in the `application.conf`:

```hocon
# include the default configuration
include "docker-reference.conf"

# override docker object fields
docker {
  readTimeoutMs = 60000
  allowReuse = false
}
```

2. Override the `DockerSupportModule` using `overridenBy`:

```scala mdoc:to-string
import izumi.distage.docker.Docker

class CustomDockerConfigExampleModule[F[_] : TagK] extends ModuleDef {
  include(new DockerSupportModule[F] overridenBy new ModuleDef {
    make[Docker.ClientConfig].from {
      Docker.ClientConfig(
        readTimeoutMs = 10000,
        connectTimeoutMs = 10000,
        allowReuse = false,
        useRemote = true,
        useRegistry = true,
        remote = Option {
          Docker.RemoteDockerConfig(
            host = "tcp://localhost:2376",
            tlsVerify = true,
            certPath = "/home/user/.docker/certs",
            config = "/home/user/.docker"
          )
        },
        registry = Option {
          Docker.DockerRegistryConfig(
            url = "https://index.docker.io/v1/",
            username = "dockeruser",
            password = "ilovedocker",
            email = "dockeruser@github.com"
          )
        },
      )
    }
  })
}
```

### Container Reuse

By default acquiring a container resource does not always start a fresh container. Likewise on
releasing the resource the container will not be destroyed.  When a container resource is acquired,
the Docker system is inspected to determine if a matching container is already executing. If a
matching container is found this container is referenced by the
`ContainerResource`. Otherwise a fresh container is started.  In both cases the acquired
`ContainerResource` will have passed configured health checks.

#### Matching Containers for Reuse

For an existing container to be reused, all the following must be true:

- The current client config has `allowReuse == true`.
- The container config has `reuse == true`.
- The running container was created for reuse.
- The running container uses the same image as the container config.
- All ports requested in the container config must be mapped for the running container.

#### Configuring Reuse

The `ContainerDef.Config.reuse` should be false to disable reuse for a specific container.  While
`Docker.ClientConfig.allowReuse` should be false to disable reuse throughout the application.

#### Improving Reuse Performance

When utilizing reuse, the performance cost of inspecting the Docker system can be avoided using memoization roots. For
example, in this integration test the container resource is not reconstructed for each test. Because the
resource is not reconstructed there is no repeated inspection of the Docker system.

```scala mdoc:silent
class NoReuseByMemoizationExampleTest extends DistageSpecScalatest[IO] {
  override def config = super.config.copy(
    moduleOverrides = new ModuleDef {
      include(distageFrameworkModules)
      include(new PostgresDockerModule[IO])
    },
    memoizationRoots = Set(
      DIKey[PostgresDocker.Container]
    )
  )

  "distage docker" should {
    "provide a fresh container resource" in { c: PostgresDocker.Container =>
      val port = c.availablePorts.first(PostgresDocker.primaryPort)
      IO(println(s"port ${port}"))
    }

    "provide the same resource" in { c: PostgresDocker.Container =>
      val port = c.availablePorts.first(PostgresDocker.primaryPort)
      IO(println(s"port ${port}"))
    }
  }
}
```


### Examples

The `distage-example` project uses `distage-framework-docker` to provide a
[PostgresDockerModule](https://github.com/7mind/distage-example/blob/develop/src/test/scala/leaderboard/PostgresDockerModule.scala).

The `distage-framework-docker` project contains example `ContainerDef`s and modules for various
services under
[`izumi.distage.docker.examples`](https://github.com/7mind/izumi/tree/develop/distage/distage-framework-docker/src/main/scala/izumi/distage/docker/examples)
namespace.

### Tips

To kill all containers spawned by `distage`, use the following command:

```shell
docker rm -f $(docker ps -q -a -f 'label=distage.type')
```

### Troubleshooting

```
// izumi.distage.model.exceptions.ProvisioningException: Provisioner stopped after 1 instances, 2/14 operations failed:
//  - {type.izumi.distage.docker.DockerClientWrapper[=λ %0 → IO[+0]]} (distage-framework-docker.md:40), MissingInstanceException: Instance is not available in the object graph: {type.izumi.distage.docker.DockerClientWrapper[=λ %0 → IO[+0]]}.
```

The `DockerSupportModule` was not included in the application modules. The component
`DockerClientWrapper` is provided by `izumi.distage.docker.modules.DockerSupportModule`.

### References

- Introduced in [release 0.9.13](https://github.com/7mind/izumi/releases/tag/v0.9.13)
- An [example PR](https://github.com/7mind/distage-livecode/pull/2/files) showing how to use them.
- The `distage-example` [PostgresDockerModule](https://github.com/7mind/distage-example/blob/develop/src/test/scala/leaderboard/PostgresDockerModule.scala).
