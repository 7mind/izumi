distage-framework-docker
========================

@@toc { depth=2 }

### Key Features

- Provides docker containers as resources
- Reasonable defaults with flexible configuration
- Excellent for providing docker implementations of services for integration tests

### Dependencies

Add the `distage-framework-docker` library:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-framework-docker" % "$izumi.version$"
```

@@@


### Overview

The `izumi.distage.docker` types and values

#### `ContainerDef`

The `ContainerDef` is a trait that provides a high level interface for defining a docker container resource.

(example nginx http ContainerDef)

#### Extending a Container Definition

- `modifyConfig`

- `dependOnDocker` - adds a dependency on a docker container. distage ensures the requested container
  is available before the dependent is provided.

### Configuration

- `Docker.ClientConfig`
- ??? document `docker-reference.conf` variables

### Usage in Integration Tests

(an example using the above nginx http containerdef)

### Examples

The `distage-example` project uses `distage-framework-docker` to provide a
[PostgresDockerModule](https://github.com/7mind/distage-example/blob/develop/src/test/scala/leaderboard/PostgresDockerModule.scala).

The `distage-framework-docker` project contains example `ContainerDef`s and modules for various
services under
[`izumi.distage.docker.examples`](https://github.com/7mind/izumi/tree/develop/distage/distage-framework-docker/src/main/scala/izumi/distage/docker/examples)
namespace.

```scala mdoc
import izumi.distage.docker.examples.PostgresDocker
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.TagK

// a hypothetical structure for the postgres configuration used by the application.
case class PostgresServerConfig(
  port: Int
)

// a hypothetical module that binds the implementation of PostgresServerConfig
// to one provided by docker
class PostgresServerConfigDockerModule[F[_]: TagK] extends ModuleDef {
  make[PostgresServerConfig].from {
    container: PostgresDocker.Container => {
      val servicePort = container.availablePorts.availablePorts(PostgresDocker.primaryPort).head
      PostgresServerConfig(servicePort.port)
    }
  }

  make[PostgresDocker.Container].fromResource {
    PostgresDocker.make[F]
  }
}

```

### Tips

To kill all containers spawned by distage, use the following command:

```shell
docker rm -f $(docker ps -q -a -f 'label=distage.type')
```

### References

- Introduced in [release 0.9.13](https://github.com/7mind/izumi/releases/tag/v0.9.13)
- An [example PR](https://github.com/7mind/distage-livecode/pull/2/files) showing how to use them.
