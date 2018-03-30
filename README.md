[![Build Status](https://travis-ci.org/pshirshov/izumi-r2.svg?branch=develop)](https://travis-ci.org/pshirshov/izumi-r2)
[![codecov](https://codecov.io/gh/pshirshov/izumi-r2/branch/develop/graph/badge.svg)](https://codecov.io/gh/pshirshov/izumi-r2)
[![Latest Release](https://img.shields.io/github/tag/pshirshov/izumi-r2.svg)](https://github.com/pshirshov/izumi-r2/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.pshirshov.izumi.r2/izumi-r2_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.github.pshirshov.izumi.r2%22)
[![Sonatype releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/com.github.pshirshov.izumi.r2/izumi-r2_2.12.svg)](https://oss.sonatype.org/content/repositories/releases/com/github/pshirshov/izumi/r2/)
[![Sonatype snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/com.github.pshirshov.izumi.r2/izumi-r2_2.12.svg)](https://oss.sonatype.org/content/repositories/snapshots/com/github/pshirshov/izumi/r2/)
[![License](https://img.shields.io/github/license/pshirshov/izumi-r2.svg)](https://github.com/pshirshov/izumi-r2/blob/develop/LICENSE)

What is it?
===========

Izumi (*jap. 泉水, spring*) is a set of non-coupled tools allowing you to significantly increase productivity of your Scala development.
 
including the following components:

1. Generative and introspectable runtime DI framework, [distage](doc/md/distage.md) 
2. Effortless structured logging framework, [logstage](doc/md/distage.md)
3. Staged Interface Definition and Data Modeling language, [idealingua](doc/md/idealingua/idealingua.md) supporting many target languages including Go, Typescript and C#
4. A set of opinionated [SBT plugins](doc/md/sbt.md) allowing you to significantly increase clarity of your builds and compactify build files
5. [Percept-plan-execute-repeat (PPER)](doc/md/pper.md) toolchain, allowing you to model very complex domain and orchestrate deadly complex processes without an issue

The work is still in progress. We are looking for early adopters, contributors and sponsors.

In the future we are going to (or just may) implement the following tools based on PPER approach:

1. Best in the world build system
2. Best in the world cluster orchestration tool
3. Best in the world load testing/macrobenchmark tool

Key goals 
=========

We aim to provide tools which are:

1. Boosting productiviy 
2. Non-invasive as it possible
3. Introspectable
4. Better than anything else :3

See also
--------

- [Build notes](doc/md/build.md)
- [Project flow](doc/md/flow.md)

Legacy Framework
----------------

Just for those who uses old stuff: you still may find it [here](https://github.com/pshirshov/izumi-legacy).
