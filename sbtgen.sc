#!/bin/sh
coursier launch com.lihaoyi:ammonite_2.13.0:1.6.9 --fork -M ammonite.Main -- sbtgen.sc $*
exit
!#
import $file.project.Deps, Deps._

@main
def entrypoint(args: String*) = Izumi.entrypoint(args)

