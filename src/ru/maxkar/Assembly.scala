package ru.maxkar

import sbt._
import Keys._

import java.util.jar.Manifest

import scala.collection.mutable.{Map ⇒ MMap, HashMap}

/** Custom assembly configuration. */
object Assembly {
  val assemblyExplodedDir = settingKey[File]("Target directory for the assembly")

  val assemblyAppName = settingKey[String](
    "Name of the application. It would be used a name for the main jar")


  val assemblyAppClass = taskKey[Option[String]](
    "Main class in the application")

  /** Full classpath to be included into the assembly. Defaults to
   * full classpath in Compile. */
  val assemblyClasspath = taskKey[Seq[File]](
    "Full classpath of the application to be built.")

  val assemblyExploded = taskKey[Option[File]](
    "Assemblies all data into the exploded directory. " +
    "Does not work without main class.")

  val assemblyImplodedFile = taskKey[File]("Target app file")

  val assemblyImploded = taskKey[Unit](
    "Assemblies a release file")


  val defaultSettings : Seq[Setting[_]] = Seq(
    assemblyExplodedDir := target.value / "assembly",
    assemblyAppName := baseDirectory.value.getName,
    assemblyAppClass := (mainClass in Compile).value,
    assemblyClasspath := (fullClasspath in Compile).value.map(_.data),
    assemblyImplodedFile := target.value / (assemblyAppName.value + ".zip"),

    assemblyExploded := assemblyAppClass.value.map(
      cls ⇒ {
        val tgt = assemblyExplodedDir.value
        IO.delete(tgt)
        buildExploded(tgt, assemblyAppName.value, cls, assemblyClasspath.value)
        tgt
      }),

    assemblyImploded := assemblyExploded.value.map(
      exploded ⇒ implode(assemblyImplodedFile.value, assemblyAppName.value, exploded))
  )


  /** Compress an exploded file into the archive. */
  private def implode(tgt : File, appName : String, exploded : File) : Unit = {
    val rm = new HashMap[String, File]
    collectFiles(rm, appName, exploded)

    IO.zip(rm.toSeq.map(x ⇒ (x._2, x._1)), tgt)
  }


  /** Builds exploded installation diectory. */
  private def buildExploded(
        explodedDir : File,
        appName : String,
        mainClass : String,
        classpath : Seq[File])
      : Unit = {
    val libdir = explodedDir / "lib"
    libdir.mkdirs

    val (classDirs, classJars) =
      classpath.toSet.toSeq.partition(_.isDirectory)

    val copiedJars = classJars.map(jar ⇒ "lib/" + copyJar(libdir, jar))
    val appJar = explodedDir / (appName + ".jar")

    val manifest = createManifest(mainClass, copiedJars)
    val resourceDefs = new HashMap[String, File]()
    classDirs.foreach(collectFiles(resourceDefs, "", _))

    IO.jar(resourceDefs.toSeq.map(x ⇒ (x._2, x._1)), appJar, manifest)
  }



  /** Collects all the files into the resource map. */
  private def collectFiles(tgt : MMap[String, File], name : String, base : File) : Unit = {
    val children = base.listFiles()
    if (children != null) {
      val pfx = if (name == "") "" else name + "/"
      children.foreach(c ⇒ collectFiles(tgt, pfx + c.getName, c))
    } else
      tgt.get(name) match {
        case Some(x) ⇒
          throw new IllegalStateException(
            "Entry " + name + " is provided by both " + x + " and " + base)
        case None ⇒ tgt.put(name, base)
      }
  }



  /** Creates a manifest for the executable. */
  private def createManifest(mainClass : String, libs : Seq[String]) : Manifest = {
    val res = new Manifest()
    val mainAttrs = res.getMainAttributes
    if (!libs.isEmpty)
      mainAttrs.putValue("Class-Path", libs.mkString(" "))
    mainAttrs.putValue("Main-Class", mainClass)
    res
  }




  /** Copies one jar into the target directory. Returns a new file name. */
  private def copyJar(dst : File, src : File) : String = {
    val name = getTargetName(dst, src.getName)
    IO.copyFile(src, dst / name)
    name
  }



  /** Returns a target de-duped name. */
  private def getTargetName(dst : File, base : String) : String = {
    if (!(dst / base).exists) return base

    val sep = base.lastIndexOf('.')
    val (pfx, sfx) =
      if (sep < 0) (base + "_", "")
      else (base.substring(0, sep) + "_", base.substring(sep))

    var itr = 0
    while ((dst / (pfx + itr + sfx)).exists)
      itr += 1

    (pfx + itr + sfx)
  }
}
