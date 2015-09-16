/* Copyright 2015 UniCredit S.p.A.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package eu.unicredit.swagger

import sbt._
import Keys._

import java.io.File.separator

object SwaggerCodegenPlugin extends AutoPlugin {

  object FileSplittingModes {
    case object SingleFile
    case object OneFilePerSource
    case object OneFilePerModel

    def apply(s: String) =
      s match {
        case "singleFile" => SingleFile
        case "oneFilePerSource" => OneFilePerSource
        case "oneFilePerModel" => OneFilePerModel
        case any =>
          throw new Exception(s"Unsupported swaggerModelFileSplitting option $any please choose one of (singleFile | oneFilePerSource | oneFilePerModel)")
      }
  }

  object autoImport {

    /*
      * Settings
      */
    val swaggerSourcesDir = settingKey[String]("swaggerSourcesDir")

    val swaggerModelCodeTargetDir = settingKey[String]("swaggerModelCodeTargetDir")

    val swaggerPlayClientCodeTargetDir = settingKey[String]("swaggerPlayClientCodeTargetDir")

    val swaggerPlayServerRoutesFile = settingKey[String]("swaggerPlayServerRoutesFile")

    val swaggerCodegenPackage = settingKey[String]("swaggerCodegenPackage")

    val swaggerModelFilesSplitting = settingKey[String]("swaggerModelFileSplitting")

    val swaggerGeneratePlayJsonRW = settingKey[Boolean]("swaggerGeneratePlayJsonRW")

    val swaggerGeneratePlayControllers = settingKey[Boolean]("swaggerGeneratePlayControllers")

    val swaggerCodeProvidedPackage = settingKey[String]("swaggerCodeProvidedPackage")

    val swaggerServerAsync = settingKey[Boolean]("swaggerServerAsync")

    /*
     * Tasks
     */
    val swaggerCleanTask = taskKey[Unit]("Clean swagger generated packages")

    val swaggerCodeGenTask = taskKey[Unit]("Generate swagger models and json converters")

    val swaggerPlayServerCodeGenTask = taskKey[Unit]("Generate swagger Play server routes and controllers boilerplate")

    val swaggerPlayClientCodeGenTask = taskKey[Unit]("Generate swagger Play client class with WS calls to specific routes")

  }

  final val swaggerCodegenPackageDefault = "swagger.codegen"
  final val swaggerSourcesDirDefault = s"${separator}src${separator}main${separator}swagger"
  final val swaggerModelCodeTargetDirDefault = s"${separator}src${separator}main${separator}scala"
  final val swaggerPlayClientCodeTargetDirDefault = s"${separator}src${separator}main${separator}scala"
  final val swaggerPlayServerRoutesFileDefault = s"${separator}src${separator}main${separator}resources${separator}routes"
  final val swaggerModelFilesSplittingDefault = "singleFile"
  final val swaggerGeneratePlayJsonRWDefault = true
  final val swaggerGeneratePlayControllersDefault = true
  final val swaggerCodeProvidedPackageDefault = "eu.unicredit"
  final val swaggerServerAsyncDefault = false

  import autoImport._
  override def trigger = allRequirements
  override lazy val buildSettings = Seq(
    commands ++= Seq(
      swaggerCleanCommand,
      swaggerCodeGenCommand,
      swaggerPlayServerCodeGenCommand,
      swaggerPlayClientCodeGenCommand))

  override val projectSettings = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % "2.4.0", //always imported???
      "joda-time" % "joda-time" % "2.7",
      "org.joda" % "joda-convert" % "1.7"),
    swaggerCleanTask := {
      val base = baseDirectory.value.getAbsolutePath
      val codegenPackage = swaggerCodegenPackage.?.value getOrElse swaggerCodegenPackageDefault

      swaggerCleanImpl(base, codegenPackage)
    },
    swaggerCodeGenTask := {
      val base = baseDirectory.value.getAbsolutePath
      val codegenPackage = swaggerCodegenPackage.?.value getOrElse swaggerCodegenPackageDefault
      val sourcesDir = swaggerSourcesDir.?.value getOrElse (base + swaggerSourcesDirDefault)
      val targetDir = swaggerModelCodeTargetDir.?.value getOrElse (base + swaggerModelCodeTargetDirDefault)
      val fileSplittingMode = swaggerModelFilesSplitting.?.value getOrElse swaggerModelFilesSplittingDefault
      val generatePlayJson = swaggerGeneratePlayJsonRW.?.value getOrElse swaggerGeneratePlayJsonRWDefault

      swaggerCodeGenImpl(base, codegenPackage, sourcesDir, fileSplittingMode, generatePlayJson, targetDir)
    },
    swaggerPlayServerCodeGenTask := {
      val base = baseDirectory.value.getAbsolutePath
      val codegenPackage = swaggerCodegenPackage.?.value getOrElse swaggerCodegenPackageDefault
      val sourcesDir = swaggerSourcesDir.?.value getOrElse (base + swaggerSourcesDirDefault)
      val targetRoutesFile = swaggerPlayServerRoutesFile.?.value getOrElse (base + swaggerPlayServerRoutesFileDefault)
      val codeProvidedPackage = swaggerCodeProvidedPackage.?.value getOrElse swaggerCodeProvidedPackageDefault
      val async = swaggerServerAsync.?.value getOrElse swaggerServerAsyncDefault
      val generateControllers = swaggerGeneratePlayControllers.?.value getOrElse swaggerGeneratePlayControllersDefault

      swaggerPlayServerCodeGenImpl(base, codegenPackage, sourcesDir, codeProvidedPackage, async, targetRoutesFile, generateControllers)
    },
    swaggerPlayClientCodeGenTask := {
      val base = baseDirectory.value.getAbsolutePath
      val codegenPackage = swaggerCodegenPackage.?.value getOrElse swaggerCodegenPackageDefault
      val sourcesDir = swaggerSourcesDir.?.value getOrElse (base + swaggerSourcesDirDefault)
      val targetDir = swaggerPlayClientCodeTargetDir.?.value getOrElse (base + swaggerPlayClientCodeTargetDirDefault)

      swaggerPlayClientCodeGenImpl(base, codegenPackage, sourcesDir, targetDir)
    })

  lazy val swaggerCleanCommand =
    Command.command("swaggerClean") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse swaggerCodegenPackageDefault

      swaggerCleanImpl(base, codegenPackage)

      state
    }

  def swaggerCleanImpl(base: String, codegenPackage: String) = {
    val destDir = FolderCreator.genPackage(s"$base${separator}src${separator}main${separator}scala", codegenPackage)

    def rm_r(f: java.io.File): Unit = {
      if (f.isDirectory)
        f.listFiles().foreach(rm_r)

      f.delete()
    }

    val routesFile = new java.io.File(s"$base$swaggerPlayServerRoutesFileDefault")

    if (routesFile.exists)
      routesFile.delete

    rm_r(destDir)
  }

  lazy val swaggerCodeGenCommand =
    Command.command("swaggerCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse swaggerCodegenPackageDefault

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse (base + swaggerSourcesDirDefault)

      val targetDir: String =
        swaggerModelCodeTargetDir in currentRef get structure.data getOrElse (base + swaggerModelCodeTargetDirDefault)

      val fileSplittingMode: String =
        swaggerModelFilesSplitting in currentRef get structure.data getOrElse swaggerModelFilesSplittingDefault

      val generatePlayJson: Boolean =
        swaggerGeneratePlayJsonRW in currentRef get structure.data getOrElse swaggerGeneratePlayJsonRWDefault

      swaggerCodeGenImpl(base, codegenPackage, sourcesDir, fileSplittingMode, generatePlayJson, targetDir)

      state
    }

  def swaggerCodeGenImpl(base: String, codegenPackage: String, sourcesDir: String, fileSplittingMode: String, generatePlayJson: Boolean, targetDir: String) = {
    val sDir = new File(sourcesDir)

    val models: Map[String, Iterable[(String, String)]] =
      if (sDir.exists() && sDir.isDirectory) {
        (for {
          file <- sDir.listFiles()
          fName = file.getName
          fPath = file.getAbsolutePath
          if fName.endsWith(".json") || fName.endsWith(".yaml")
        } yield {
          fName -> CodeGen.generateModels(fPath)
        }).toMap
      } else Map()

    val jsonFormats =
      CodeGen.generateJsonRW(
        if (sDir.exists() && sDir.isDirectory) {
          (for {
            file <- sDir.listFiles()
            fName = file.getName
            fPath = file.getAbsolutePath
            if fName.endsWith(".json") || fName.endsWith(".yaml")
          } yield {
            fPath
          }).toList
        } else List())

    val destDir = FolderCreator.genPackage(targetDir, codegenPackage)

    def getFileName(s: String) =
      new String(s.toCharArray.toList.takeWhile(_ == '.').toArray)

    import FileSplittingModes._
    FileSplittingModes(fileSplittingMode) match {
      case SingleFile =>
        val code =
          CodeGen.generateModelInit(codegenPackage) +
            models.values.flatten.map(_._2).toList.distinct.mkString("\n", "\n\n", "\n")

        FileWriter.writeToFile(new File(destDir, "Model.scala"), code)
      case OneFilePerSource =>
        models.map {
          case (k, m) =>
            k ->
              (CodeGen.generateModelInit(codegenPackage) +
                m.map(_._2).toList.distinct.mkString("\n", "\n\n", "\n"))
        }.foreach {
          case (k, code) =>
            FileWriter.writeToFile(new File(destDir, s"${getFileName(k)}.scala"), code)
        }
      case OneFilePerModel =>
        models.values.flatten.foreach {
          case (k, v) =>
            val code =
              CodeGen.generateModelInit(codegenPackage) + "\n" + v
            FileWriter.writeToFile(new File(destDir, s"${getFileName(k)}.scala"), code)
        }
    }

    if (generatePlayJson) {
      val jsonDir = FolderCreator.genPackage(destDir.getAbsolutePath, "json")
      val code =
        CodeGen.generateJsonInit(codegenPackage) + "\n\n" +
          CodeGen.generateJsonImplicits(jsonFormats)

      FileWriter.writeToFile(new File(jsonDir, s"package.scala"), code)
    }
  }

  lazy val swaggerPlayServerCodeGenCommand =
    Command.command("swaggerPlayServerCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse swaggerCodegenPackageDefault

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse (base + swaggerSourcesDirDefault)

      val targetRoutesFile: String =
        swaggerPlayServerRoutesFile in currentRef get structure.data getOrElse (base + swaggerPlayServerRoutesFileDefault)

      val codeProvidedPackage: String =
        swaggerCodeProvidedPackage in currentRef get structure.data getOrElse swaggerCodeProvidedPackageDefault

      val async: Boolean =
        swaggerServerAsync in currentRef get structure.data getOrElse swaggerServerAsyncDefault

      val generateControllers =
        swaggerGeneratePlayControllers in currentRef get structure.data getOrElse swaggerGeneratePlayControllersDefault

      swaggerPlayServerCodeGenImpl(base, codegenPackage, sourcesDir, codeProvidedPackage, async, targetRoutesFile, generateControllers)

      state
    }

  def swaggerPlayServerCodeGenImpl(base: String, codegenPackage: String, sourcesDir: String, codeProvidedPackage: String, async: Boolean, targetRoutesFile: String, generateControllers: Boolean) = {
    val sDir = new File(sourcesDir)

    val routes: Map[String, Seq[String]] =
      if (sDir.exists() && sDir.isDirectory) {
        (for {
          file <- sDir.listFiles()
          fName = file.getName
          fPath = file.getAbsolutePath
          if fName.endsWith(".json") || fName.endsWith(".yaml")
        } yield {
          fPath -> CodeGen.generatePlayServerRoutes(fPath, codegenPackage)
        }).toMap
      } else Map()

    val sr =
      routes.values.flatten.toList.distinct.mkString("\n", "\n\n", "\n")

    FileWriter.writeToFile(new File(targetRoutesFile), sr)

    if (generateControllers) {
      val controllers: Map[String, String] =
        if (sDir.exists() && sDir.isDirectory) {
          (for {
            file <- sDir.listFiles()
            fName = file.getName
            fPath = file.getAbsolutePath
            if fName.endsWith(".json") || fName.endsWith(".yaml")
          } yield {
            CodeGen.generatePlayServerStub(fPath, codegenPackage, codeProvidedPackage, async)
          }).toMap
        } else Map()

      val destDir = FolderCreator.genPackage(s"$base${separator}src${separator}main${separator}scala", codegenPackage + ".controller")

      controllers.foreach {
        case (fName, code) => FileWriter.writeToFile(new File(destDir, fName + ".scala"), code)
      }
    }
  }

  lazy val swaggerPlayClientCodeGenCommand =
    Command.command("swaggerPlayClientCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse swaggerCodegenPackageDefault

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse (base + swaggerSourcesDirDefault)

      val targetDir: String =
        swaggerPlayClientCodeTargetDir in currentRef get structure.data getOrElse (base + swaggerPlayClientCodeTargetDirDefault)

      swaggerPlayClientCodeGenImpl(base, codegenPackage, sourcesDir, targetDir)

      state
    }

  def swaggerPlayClientCodeGenImpl(base: String, codegenPackage: String, sourcesDir: String, targetDir: String) = {
    val sDir = new File(sourcesDir)

    val clients: Map[String, String] =
      if (sDir.exists() && sDir.isDirectory) {
        (for {
          file <- sDir.listFiles()
          fName = file.getName
          fPath = file.getAbsolutePath
          if fName.endsWith(".json") || fName.endsWith(".yaml")
        } yield {
          CodeGen.generatePlayClientStub(fPath, codegenPackage)
        }).toMap
      } else Map()

    val destDir = FolderCreator.genPackage(targetDir, codegenPackage + ".client")

    clients.foreach {
      case (fName, code) => FileWriter.writeToFile(new File(destDir, fName + ".scala"), code)
    }

  }
}
