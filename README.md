[![Build Status](https://travis-ci.com/7mind/izumi.svg?branch=develop)](https://travis-ci.com/7mind/izumi)
[![codecov](https://codecov.io/gh/7mind/izumi/branch/develop/graph/badge.svg)](https://codecov.io/gh/7mind/izumi)
[![CodeFactor](https://www.codefactor.io/repository/github/7mind/izumi/badge)](https://www.codefactor.io/repository/github/7mind/izumi)
[![Latest Release](https://img.shields.io/github/tag/7mind/izumi.svg)](https://github.com/7mind/izumi/releases)
[![Maven Central](https://img.shields.io/maven-central/v/io.7mind.izumi/izumi-r2_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.7mind.izumi%22)
[![Sonatype releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/io.7mind.izumi/izumi-r2_2.12.svg)](https://oss.sonatype.org/content/repositories/releases/io/7mind/izumi/)
[![Sonatype snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/io.7mind.izumi/izumi-r2_2.12.svg)](https://oss.sonatype.org/content/repositories/snapshots/io/7mind/izumi/)
[![License](https://img.shields.io/github/license/7mind/izumi.svg)](https://github.com/7mind/izumi/blob/develop/LICENSE) 
[![Gitter](https://badges.gitter.im/7mind/izumi.svg)](https://gitter.im/7mind/izumi)
[![Awesome](https://cdn.rawgit.com/sindresorhus/awesome/d7305f38d29fed78fa85652e3a63e154dd8e8829/media/badge.svg)](https://github.com/lauris/awesome-scala)
[![Latest version](https://index.scala-lang.org/pshirshov/izumi-r2/izumi-r2/latest.svg?color=orange)](https://index.scala-lang.org/pshirshov/izumi-r2/izumi-r2)
![:3](https://img.shields.io/badge/🔥-Blazing%20Fast-red.svg)
![:3](https://img.shields.io/badge/😿-For%20Humans-blue.svg)
![:3](https://img.shields.io/badge/🏢-Enterprise%20Grade-999999.svg)
![:3](https://img.shields.io/badge/👌-Production%20Ready-00ddcc.svg)
![:3](https://img.shields.io/badge/💎-Modern-44aadd.svg)
![:3](https://img.shields.io/badge/🦋-Extremely%20Lightweight-7799cc.svg)
![:3](https://img.shields.io/badge/🦄-Just%20Works-cc00cc.svg)

What is it?
===========

Izumi (*jap. 泉水, spring*) is a set of independent libraries and frameworks allowing you to significantly increase productivity of your Scala development.
 
including the following components:

1. [distage](https://izumi.7mind.io/latest/release/doc/distage/) – Staged, transparent and debuggable runtime & compile-time Dependency Injection Framework,
2. [logstage](https://izumi.7mind.io/latest/release/doc/logstage/) – Automatic structural logs from Scala string interpolations,
3. [idealingua](https://izumi.7mind.io/latest/release/doc/idealingua/) – API Definition, Data Modeling and RPC Language, optimized for fast prototyping – like gRPC, but with a human face. Currently generates servers and clients for Go, TypeScript, C# and Scala,
4. [Opinionated SBT plugins](https://izumi.7mind.io/latest/release/doc/sbt/) – Reduces verbosity of SBT builds and introduces new features – inter-project shared test scopes and BOM plugins (from Maven),
5. [Percept-Plan-Execute-Repeat (PPER)](https://izumi.7mind.io/latest/release/doc/pper/) – a pattern that enables modeling very complex domains and orchestrate deadly complex processes a lot easier than you're used to.

Docs
----

* [Documentation & Tutorials](https://izumi.7mind.io/latest/release/doc/)
* [Scaladoc](https://izumi.7mind.io/latest/release/api/)

Example projects:
* [DIStage Example Project](https://github.com/7mind/distage-sample)
* [Idealingua Example Project](https://github.com/kaishh/izumi-petstore)

Slides:
* [ScalaUA Slides about DIStage](https://www.slideshare.net/7mind/scalaua-distage-staged-dependency-injection)
* [ScalaUA Slides about LogStage](https://www.slideshare.net/7mind/logstage-zerocosttructuredlogging)
* [Slides from other meetups](https://github.com/7mind/slides)

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

Credits
=======

[![YourKit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com)

YourKit supports open source projects with innovative and intelligent tools 
for monitoring and profiling Java and .NET applications.
YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), 
[YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/) and 
[YourKit YouMonitor](https://www.yourkit.com/youmonitor/).


Contributors
============

See:

- [Build notes](doc/md/build.md)
- [Project flow](doc/md/flow.md)

