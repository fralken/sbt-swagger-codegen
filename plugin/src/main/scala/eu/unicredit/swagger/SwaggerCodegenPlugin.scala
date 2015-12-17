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

import eu.unicredit.swagger.generators._

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

    val swaggerModelCodeGenClass = settingKey[ModelGenerator]("swaggerModelCodeGenClass")

    val swaggerJsonCodeGenClass = settingKey[JsonGenerator]("swaggerJsonCodeGenClass")

    val swaggerServerCodeGenClass = settingKey[ServerGenerator]("swaggerServerCodeGenClass")

    val swaggerClientCodeGenClass = settingKey[ClientGenerator]("swaggerClientCodeGenClass")

    val swaggerModelCodeTargetDir = settingKey[String]("swaggerModelCodeTargetDir")

    val swaggerClientCodeTargetDir = settingKey[String]("swaggerClientCodeTargetDir")

    val swaggerServerRoutesFile = settingKey[String]("swaggerServerRoutesFile")

    val swaggerCodeGenPackage = settingKey[String]("swaggerCodeGenPackage")

    val swaggerModelFilesSplitting = settingKey[String]("swaggerModelFileSplitting")

    val swaggerGenerateJsonRW = settingKey[Boolean]("swaggerGenerateJsonRW")

    val swaggerGenerateControllers = settingKey[Boolean]("swaggerGenerateControllers")

    val swaggerCodeProvidedPackage = settingKey[String]("swaggerCodeProvidedPackage")

    /*
     * Tasks
     */
    val swaggerCleanTask = taskKey[Unit]("Clean swagger generated packages")

    val swaggerCodeGenTask = taskKey[Unit]("Generate swagger models and json converters")

    val swaggerServerCodeGenTask = taskKey[Unit]("Generate swagger server routes and controllers boilerplate")

    val swaggerClientCodeGenTask = taskKey[Unit]("Generate swagger client class with WS calls to specific routes")

  }

  final val swaggerCodeGenPackageDefault = "swagger.codegen"
  final val swaggerSourcesDirDefault = s"${separator}src${separator}main${separator}swagger"
  final val swaggerModelCodeTargetDirDefault = s"${separator}src${separator}main${separator}scala"
  final val swaggerClientCodeTargetDirDefault = s"${separator}src${separator}main${separator}scala"
  final val swaggerServerRoutesFileDefault = s"${separator}src${separator}main${separator}resources${separator}routes"
  final val swaggerModelFilesSplittingDefault = "singleFile"
  final val swaggerGenerateJsonRWDefault = true
  final val swaggerGenerateControllersDefault = true
  final val swaggerCodeProvidedPackageDefault = "com.yourcompany"

  final val swaggerModelCodeGenClassDefault = new DefaultModelGenerator()
  final val swaggerJsonCodeGenClassDefault = new DefaultJsonGenerator()
  final val swaggerServerCodeGenClassDefault = new DefaultServerGenerator()
  final val swaggerClientCodeGenClassDefault = new DefaultClientGenerator()

  import autoImport._
  override def trigger = allRequirements
  override lazy val buildSettings = Seq(
    commands ++= Seq(
      swaggerCleanCommand,
      swaggerCodeGenCommand,
      swaggerServerCodeGenCommand,
      swaggerClientCodeGenCommand))

  override val projectSettings = {
    Seq(
    swaggerCleanTask := {
      val base = baseDirectory.value.getAbsolutePath
      val codegenPackage = swaggerCodeGenPackage.?.value getOrElse swaggerCodeGenPackageDefault

      swaggerCleanImpl(base, codegenPackage)
    },
    swaggerCodeGenTask := {
      val base = baseDirectory.value.getAbsolutePath
      val codegenPackage = swaggerCodeGenPackage.?.value getOrElse swaggerCodeGenPackageDefault
      val sourcesDir = swaggerSourcesDir.?.value getOrElse (base + swaggerSourcesDirDefault)
      val targetDir = swaggerModelCodeTargetDir.?.value getOrElse (base + swaggerModelCodeTargetDirDefault)
      val fileSplittingMode = swaggerModelFilesSplitting.?.value getOrElse swaggerModelFilesSplittingDefault
      val generateJson = swaggerGenerateJsonRW.?.value getOrElse swaggerGenerateJsonRWDefault

      val modelGenerator = swaggerModelCodeGenClass.?.value getOrElse swaggerModelCodeGenClassDefault
      val jsonGenerator = swaggerJsonCodeGenClass.?.value getOrElse swaggerJsonCodeGenClassDefault

      swaggerCodeGenImpl(base, codegenPackage, sourcesDir, fileSplittingMode, generateJson, targetDir, modelGenerator, jsonGenerator)
    },
    swaggerServerCodeGenTask := {
      val base = baseDirectory.value.getAbsolutePath
      val codegenPackage = swaggerCodeGenPackage.?.value getOrElse swaggerCodeGenPackageDefault
      val sourcesDir = swaggerSourcesDir.?.value getOrElse (base + swaggerSourcesDirDefault)
      val targetRoutesFile = swaggerServerRoutesFile.?.value getOrElse (base + swaggerServerRoutesFileDefault)
      val codeProvidedPackage = swaggerCodeProvidedPackage.?.value getOrElse swaggerCodeProvidedPackageDefault
      val generateControllers = swaggerGenerateControllers.?.value getOrElse swaggerGenerateControllersDefault

      val serverGenerator = swaggerServerCodeGenClass.?.value getOrElse swaggerServerCodeGenClassDefault

      swaggerServerCodeGenImpl(base, codegenPackage, sourcesDir, codeProvidedPackage, targetRoutesFile, generateControllers, serverGenerator)
    },
    swaggerClientCodeGenTask := {
      val base = baseDirectory.value.getAbsolutePath
      val codegenPackage = swaggerCodeGenPackage.?.value getOrElse swaggerCodeGenPackageDefault
      val sourcesDir = swaggerSourcesDir.?.value getOrElse (base + swaggerSourcesDirDefault)
      val targetDir = swaggerClientCodeTargetDir.?.value getOrElse (base + swaggerClientCodeTargetDirDefault)

      val clientGenerator = swaggerClientCodeGenClass.?.value getOrElse swaggerClientCodeGenClassDefault

      swaggerClientCodeGenImpl(base, codegenPackage, sourcesDir, targetDir, clientGenerator)
    })
  }

  lazy val swaggerCleanCommand =
    Command.command("swaggerClean") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodeGenPackage in currentRef get structure.data getOrElse swaggerCodeGenPackageDefault

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

    val routesFile = new java.io.File(s"$base$swaggerServerRoutesFileDefault")

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
        swaggerCodeGenPackage in currentRef get structure.data getOrElse swaggerCodeGenPackageDefault

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse (base + swaggerSourcesDirDefault)

      val targetDir: String =
        swaggerModelCodeTargetDir in currentRef get structure.data getOrElse (base + swaggerModelCodeTargetDirDefault)

      val fileSplittingMode: String =
        swaggerModelFilesSplitting in currentRef get structure.data getOrElse swaggerModelFilesSplittingDefault

      val generateJson: Boolean =
        swaggerGenerateJsonRW in currentRef get structure.data getOrElse swaggerGenerateJsonRWDefault

      val modelGenerator: ModelGenerator =
        swaggerModelCodeGenClass in currentRef get structure.data getOrElse swaggerModelCodeGenClassDefault

      val jsonGenerator: JsonGenerator =
        swaggerJsonCodeGenClass in currentRef get structure.data getOrElse swaggerJsonCodeGenClassDefault

      swaggerCodeGenImpl(base, codegenPackage, sourcesDir, fileSplittingMode, generateJson, targetDir, modelGenerator, jsonGenerator)

      state
    }

  def swaggerCodeGenImpl(base: String,
                         codegenPackage: String,
                         sourcesDir: String,
                         fileSplittingMode: String,
                         generateJson: Boolean,
                         targetDir: String,
                         modelGenerator: ModelGenerator,
                         jsonGenerator: JsonGenerator) = {

    val sDir = new File(sourcesDir)
    checkFileExistence(sDir)

    val models: Map[String, Iterable[SyntaxString]] =
        (for {
          file <- sDir.listFiles()
          fName = file.getName
          fPath = file.getAbsolutePath
          if fName.endsWith(".json") || fName.endsWith(".yaml")
        } yield {
          fName -> modelGenerator.generate(fPath, codegenPackage)
        }).toMap

    val jsonFormats: List[SyntaxString] =
        (for {
          file <- sDir.listFiles()
          fName = file.getName
          fPath = file.getAbsolutePath
          if fName.endsWith(".json") || fName.endsWith(".yaml")
        } yield {
          jsonGenerator.generate(fPath, codegenPackage).toList
        }).flatten.toList

    val destDir = FolderCreator.genPackage(targetDir, codegenPackage)

    def getFileName(s: String) =
      new String(s.toCharArray.toList.takeWhile(_ == '.').toArray)

    import FileSplittingModes._
    FileSplittingModes(fileSplittingMode) match {
      case SingleFile =>
        val code =
          models.values.flatten.flatMap(_.pre.split("\n")).toList.distinct.mkString("\n") +
            models.values.flatten.map(_.code).toList.distinct.mkString("\n\n", "\n\n", "\n")

        FileWriter.writeToFile(new File(destDir, "Model.scala"), code)
      case OneFilePerSource =>
        models.map {
          case (k, m) =>
            k ->
              (m.flatMap(_.pre.split("\n")).toList.distinct.mkString("\n") +
                m.map(_.code).toList.distinct.mkString("\n\n", "\n\n", "\n"))
        }.foreach {
          case (k, code) =>
            FileWriter.writeToFile(new File(destDir, s"${getFileName(k)}.scala"), code)
        }
      case OneFilePerModel =>
        models.values.flatten.foreach {v =>
            val code =
              v.pre + "\n\n" + v.code
            FileWriter.writeToFile(new File(destDir, s"${getFileName(v.name)}.scala"), code)
        }
    }

    if (generateJson) {
      val jsonDir = FolderCreator.genPackage(destDir.getAbsolutePath, "json")
      val code =
        jsonFormats.flatMap(_.pre.split("\n")).distinct.mkString("\n") +
          jsonFormats.map(_.code).mkString("\n\n", "\n\n", "\n")

      FileWriter.writeToFile(new File(jsonDir, s"package.scala"), code)
    }
  }

  lazy val swaggerServerCodeGenCommand =
    Command.command("swaggerServerCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodeGenPackage in currentRef get structure.data getOrElse swaggerCodeGenPackageDefault

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse (base + swaggerSourcesDirDefault)

      val targetRoutesFile: String =
        swaggerServerRoutesFile in currentRef get structure.data getOrElse (base + swaggerServerRoutesFileDefault)

      val codeProvidedPackage: String =
        swaggerCodeProvidedPackage in currentRef get structure.data getOrElse swaggerCodeProvidedPackageDefault

      val generateControllers =
        swaggerGenerateControllers in currentRef get structure.data getOrElse swaggerGenerateControllersDefault

      val serverGenerator: ServerGenerator =
        swaggerServerCodeGenClass in currentRef get structure.data getOrElse swaggerServerCodeGenClassDefault

      swaggerServerCodeGenImpl(base, codegenPackage, sourcesDir, codeProvidedPackage, targetRoutesFile, generateControllers, serverGenerator)

      state
    }

  def swaggerServerCodeGenImpl(base: String, codegenPackage: String, sourcesDir: String, codeProvidedPackage: String, targetRoutesFile: String, generateControllers: Boolean, serverGenerator: ServerGenerator) = {
    val sDir = new File(sourcesDir)
    checkFileExistence(sDir)

    val routes: String =
        (for {
          file <- sDir.listFiles()
          fName = file.getName
          fPath = file.getAbsolutePath
          if fName.endsWith(".json") || fName.endsWith(".yaml")
        } yield {
          serverGenerator.generateRoutes(fPath, codegenPackage)
        }).flatten.mkString("\n")

    val sr =
      routes.split("\n").toList.distinct.mkString("\n", "\n\n", "\n")

    FileWriter.writeToFile(new File(targetRoutesFile), sr)

    if (generateControllers) {
      val controllers: List[SyntaxString] =
          (for {
            file <- sDir.listFiles()
            fName = file.getName
            fPath = file.getAbsolutePath
            if fName.endsWith(".json") || fName.endsWith(".yaml")
          } yield {
            serverGenerator.generate(fPath, codegenPackage, codeProvidedPackage)
          }).flatten.toList

      val destDir = FolderCreator.genPackage(s"$base${separator}src${separator}main${separator}scala", codegenPackage + ".controller")

      controllers.foreach {
        case ss => FileWriter.writeToFile(new File(destDir, ss.name + ".scala"), ss.pre+"\n\n"+ss.code)
      }
    }
  }

  lazy val swaggerClientCodeGenCommand =
    Command.command("swaggerClientCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodeGenPackage in currentRef get structure.data getOrElse swaggerCodeGenPackageDefault

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse (base + swaggerSourcesDirDefault)

      val targetDir: String =
        swaggerClientCodeTargetDir in currentRef get structure.data getOrElse (base + swaggerClientCodeTargetDirDefault)

      val clientGenerator: ClientGenerator =
        swaggerClientCodeGenClass in currentRef get structure.data getOrElse swaggerClientCodeGenClassDefault

      swaggerClientCodeGenImpl(base, codegenPackage, sourcesDir, targetDir, clientGenerator)

      state
    }

  def swaggerClientCodeGenImpl(base: String, codegenPackage: String, sourcesDir: String, targetDir: String, clientGenerator: ClientGenerator) = {
    val sDir = new File(sourcesDir)
    checkFileExistence(sDir)

    val clients: List[SyntaxString] =
        (for {
          file <- sDir.listFiles()
          fName = file.getName
          fPath = file.getAbsolutePath
          if fName.endsWith(".json") || fName.endsWith(".yaml")
        } yield {
          clientGenerator.generate(fPath, codegenPackage)
        }).flatten.toList

    val destDir = FolderCreator.genPackage(targetDir, codegenPackage + ".client")

    clients.foreach {
      case ss => FileWriter.writeToFile(new File(destDir, ss.name + ".scala"), ss.pre + "\n\n" + ss.code)
    }

  }

  def checkFileExistence(sDir: File) = {
    if (!sDir.exists() || !sDir.isDirectory)
      throw new Exception("Provided swagger source dir doesn't exists")
    else
      if (sDir.listFiles().count(x => x.getName.endsWith(".json") || x.getName.endsWith(".yaml")) < 1)
        throw new Exception("There are no files in swagger directory")
  }
}
