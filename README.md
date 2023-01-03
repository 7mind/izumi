[![Gitter](https://badges.gitter.im/7mind/izumi.svg)](https://gitter.im/7mind/izumi)
[![Patreon](https://img.shields.io/badge/patreon-sponsor-ff69b4.svg)](https://www.patreon.com/7mind)
[![Build Status](https://github.com/7mind/izumi/workflows/Build/badge.svg)](https://github.com/7mind/izumi/actions/workflows/build.yml)
[![codecov](https://codecov.io/gh/7mind/izumi/branch/develop/graph/badge.svg)](https://codecov.io/gh/7mind/izumi)
[![CodeFactor](https://www.codefactor.io/repository/github/7mind/izumi/badge)](https://www.codefactor.io/repository/github/7mind/izumi)
[![License](https://img.shields.io/github/license/7mind/izumi.svg)](https://github.com/7mind/izumi/blob/develop/LICENSE)
[![Awesome](https://cdn.rawgit.com/sindresorhus/awesome/d7305f38d29fed78fa85652e3a63e154dd8e8829/media/badge.svg)](https://github.com/lauris/awesome-scala)

<p align="center">
  <a href="https://izumi.7mind.io/">
  <img width="40%" src="https://github.com/7mind/izumi/blob/develop/doc/microsite/src/main/tut/media/izumi-logo-full-purple.png?raw=true" alt="Izumi"/>
  </a>
</p>

---

<p align="center">
  <a href="https://www.buymeacoffee.com/7mind"><img src="https://bmc-cdn.nyc3.digitaloceanspaces.com/BMC-button-images/custom_images/orange_img.png" alt="Izumi"/></a>
</p>

---

[![Latest Release](https://img.shields.io/github/tag/7mind/izumi.svg)](https://github.com/7mind/izumi/releases)
[![Maven Central](https://img.shields.io/maven-central/v/io.7mind.izumi/distage-core_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.7mind.izumi%22)
[![Sonatype releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/io.7mind.izumi/distage-core_2.12.svg)](https://oss.sonatype.org/content/repositories/releases/io/7mind/izumi/)
[![Sonatype snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/io.7mind.izumi/distage-core_2.12.svg)](https://oss.sonatype.org/content/repositories/snapshots/io/7mind/izumi/)
[![Latest version](https://index.scala-lang.org/7mind/izumi/latest.svg?color=orange)](https://index.scala-lang.org/7mind/izumi)

What is it?
===========

Izumi (*jp. 泉水, spring*) is an ecosystem of independent libraries and frameworks allowing you to significantly increase productivity of your Scala development.

including the following components:

1. [distage](https://izumi.7mind.io/distage/) – Compile-time safe, transparent and debuggable Dependency Injection framework for pure FP Scala,
2. [distage-testkit](https://izumi.7mind.io/distage/distage-testkit) – Hyper-pragmatic pure FP Test framework. Shares heavy resources globally across all test suites; lets you easily swap implementations of component; uses your effect type for parallelism.
3. [distage-framework-docker](https://izumi.7mind.io/distage/distage-framework-docker) – A distage extension for using docker containers in tests or for local application runs, comes with example Postgres, Cassandra, Kafka & DynamoDB containers.
4. [LogStage](https://izumi.7mind.io/logstage/) – Automatic structural logs from Scala string interpolations,
5. [BIO](https://izumi.7mind.io/bio/) - A typeclass hierarchy for tagless final style with Bifunctor and Trifunctor effect types. Focused on ergonomics and ease of use with zero boilerplate.
6. [izumi-reflect](https://github.com/zio/izumi-reflect) (moved to [zio/izumi-reflect](https://github.com/zio/izumi-reflect)) - Portable, lightweight and kind-polymorphic alternative to `scala-reflect`'s Typetag for Scala, Scala.js, Scala Native and Dotty
7. [IdeaLingua](https://izumi.7mind.io/idealingua/) (moved to [7mind/idealingua-v1](https://github.com/7mind/idealingua-v1)) – API Definition, Data Modeling and RPC language, optimized for fast prototyping – like gRPC or Swagger, but with a human face. Generates RPC servers and clients for Go, TypeScript, C# and Scala,
8. [Opinionated SBT plugins](https://izumi.7mind.io/sbt/) (moved to [7mind/sbtgen](https://github.com/7mind/sbtgen)) – Reduces verbosity of SBT builds and introduces new features – inter-project shared test scopes and BOM plugins (from Maven)
9. [Percept-Plan-Execute-Repeat (PPER)](https://izumi.7mind.io/pper/) – A pattern that enables modeling very complex domains and orchestrate deadly complex processes a lot easier than you're used to.

Docs
----

* **[Documentation](https://izumi.7mind.io/)**
* **[Scaladoc](https://izumi.7mind.io/latest/release/api/)**

Example projects:

* [DIStage Example Project](https://github.com/7mind/distage-example)
* [Idealingua Example Project with TypeScript and Scala](https://github.com/7mind/idealingua-example)

Support Chats:

* [Izumi on Gitter](https://gitter.im/7mind/izumi)
* [Izumi User Group [RU] on Telegram](https://t.me/izumi_ru)
* [Izumi User Group [EN] on Telegram](https://t.me/izumi_en)
* [Discussions on Github](https://github.com/7mind/izumi/discussions)

Videos:

* [Izumi 1.0: Your Next Scala Stack](https://www.youtube.com/watch?v=o65sKWnFyk0)
* [Scala, Functional Programming and Team Productivity](https://www.youtube.com/watch?v=QbdeVoL4hBk)
* [Hyper-pragmatic Pure FP Testing with distage-testkit](https://www.youtube.com/watch?v=CzpvjkUukAs)
* [Livecoding: DIStage & Bifunctor Tagless Final](https://www.youtube.com/watch?v=C0srg5T0E4o&t=4971)
* [DevInsideYou — Tagless Final with BIO](https://www.youtube.com/watch?v=ZdGK1uedAE0&t=580s)
* [Source Talks — Pragmatic Pure FP approach to application design and testing with distage](https://www.youtube.com/watch?v=W60JO3TuFhc)

Slides:

* [Izumi 1.0: Your Next Scala Stack](https://www.slideshare.net/7mind/izumi-10-your-next-scala-stack)
* [Scala, Functional Programming and Team Productivity](https://www.slideshare.net/7mind/scala-functional-programming-and-team-productivity)
* [Hyper-pragmatic Pure FP Testing with distage-testkit](https://www.slideshare.net/7mind/hyperpragmatic-pure-fp-testing-with-distagetestkit)
* [distage: Staged Dependency Injection](https://www.slideshare.net/7mind/scalaua-distage-staged-dependency-injection)
* [LogStage: Zero-cost Structured Logging](https://www.slideshare.net/7mind/logstage-zerocosttructuredlogging)
* [More slides](https://github.com/7mind/slides)

Key goals
=========

We aim to provide tools that:

1. Boost productivity and reduce code bloat
2. Are as non-invasive as possible
3. Are introspectable
4. Are better than anything else out there :3

Current state and future plans
==============================

We are looking for early adopters, contributors and sponsors.

This project is currently a work in progress.

In the future we are going to (or may) implement more tools based on PPER approach:

1. Best in the world build system
2. Best in the world cluster orchestration tool
3. Best in the world load testing/macro-benchmark tool

Adopters
========

Are you using Izumi? Please consider opening a pull request to list your organization here:

<a href="https://tinkoff.ru/">
  <img width="40%" src="https://raw.githubusercontent.com/7mind/izumi/develop/doc/microsite/src/main/tut/media/user-logo-best-bank.svg?sanitize=true" alt="Tinkoff"/>
</a>
<br/>

<a href="https://www.raiffeisen.ru/en/">
  <img width="40%" src="https://raw.githubusercontent.com/7mind/izumi/develop/doc/microsite/src/main/tut/media/user-logo-raiffeisen.svg?sanitize=true" alt="Raiffeisen Bank Russia"/>
</a>
<br/>

<a href="https://tele2.ru/">
  <img width="40%" src="https://raw.githubusercontent.com/7mind/izumi/develop/doc/microsite/src/main/tut/media/user-logo-tele2-ru.svg?sanitize=true" alt="Tele2 Russia"/>
</a>
<br/>

<a href="https://evo.company/">
  <img width="40%" src="https://raw.githubusercontent.com/7mind/izumi/develop/doc/microsite/src/main/tut/media/user-logo-evo-pay.svg?sanitize=true" alt="Evo.Pay"/>
</a>
<br/>

<a href="https://glidewell.io/">
  <img width="40%" src="https://raw.githubusercontent.com/7mind/izumi/develop/doc/microsite/src/main/tut/media/user-logo-glidewell.svg?sanitize=true" alt="Glidewell.io"/>
</a>
<br/>

Projects powered by Izumi
==========================
- [d4s](https://github.com/PlayQ/d4s) - "Dynamo DB Database done Scala way". A library that allows accessing the DynamoDB in a purely-functional way.

Credits
=======

[![YourKit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com)

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/),
[YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/) and
[YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

[![Triplequote Hydra](https://triplequote.com/img/services/hydra-2.svg)](https://triplequote.com/)

[Triplequote Hydra](https://triplequote.com/) is the world’s only parallel compiler for the Scala language. Hydra works by parallelizing all of the Scala compiler phases, taking full advantage of the many cores available in modern hardware.

Contributors
============

* Run `./sbtgen.sc` to generate a JVM-only sbt project, run `./sbtgen.sc --js` to generate a JVM+JS sbt crossproject

See:

- [Build notes](doc/md/build.md)
- [Project flow](doc/md/flow.md)
