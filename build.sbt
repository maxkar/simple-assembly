sbtPlugin := true

crossPaths := false

name := "build-assembly"

organization := "ru.maxkar"

version := "0.0.0-SNAPSHOT"

scalaSource in Compile := baseDirectory.value / "src"

target := file(".target")
