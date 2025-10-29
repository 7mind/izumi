#!/bin/sh

scala-cli ./sbtgen/ --java-home "$JAVA_HOME" --server=false --main-class Izumi -- $*
