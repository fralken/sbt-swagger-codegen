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

import java.io.File
import sbt._
import Keys._

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
          throw new Exception(
            s"Unsupported swaggerModelFileSplitting option $any please choose one of (singleFile | oneFilePerSource | oneFilePerModel)")
      }
  }

  object autoImport {

    /*
     * Settings
     */
    val swaggerSourcesDir = settingKey[File]("swaggerSourcesDir")

    val swaggerModelCodeGenClass =
      settingKey[ModelGenerator]("swaggerModelCodeGenClass")

    val swaggerJsonCodeGenClass =
      settingKey[JsonGenerator]("swaggerJsonCodeGenClass")

    val swaggerServerCodeGenClass =
      settingKey[ServerGenerator]("swaggerServerCodeGenClass")

    val swaggerClientCodeGenClass =
      settingKey[ClientGenerator]("swaggerClientCodeGenClass")

    val swaggerModelCodeTargetDir =
      settingKey[File]("swaggerModelCodeTargetDir")

    val swaggerClientCodeTargetDir =
      settingKey[File]("swaggerClientCodeTargetDir")

    val swaggerServerCodeTargetDir =
      settingKey[File]("swaggerServerCodeTargetDir")

    val swaggerServerRoutesFile = settingKey[File]("swaggerServerRoutesFile")

    val swaggerCodeGenPackage = settingKey[String]("swaggerCodeGenPackage")

    val swaggerModelFilesSplitting =
      settingKey[String]("swaggerModelFileSplitting")

    val swaggerGenerateJsonRW = settingKey[Boolean]("swaggerGenerateJsonRW")

    val swaggerGenerateControllers =
      settingKey[Boolean]("swaggerGenerateControllers")

    val swaggerCodeProvidedPackage =
      settingKey[String]("swaggerCodeProvidedPackage")

    /*
     * Tasks
     */
    val swaggerClean = taskKey[Unit]("Clean swagger generated packages")

    val swaggerCodeGen =
      taskKey[Unit]("Generate swagger models and json converters")

    val swaggerServerCodeGen = taskKey[Unit](
      "Generate swagger server routes and controllers boilerplate")

    val swaggerClientCodeGen = taskKey[Unit](
      "Generate swagger client class with WS calls to specific routes")

  }

  import autoImport._
  override def trigger = allRequirements

  override val projectSettings = {
    Seq(
      swaggerSourcesDir := baseDirectory.value / "src" / "main" / "swagger",
      swaggerModelCodeTargetDir := baseDirectory.value / "src" / "main" / "scala",
      swaggerServerCodeTargetDir := baseDirectory.value / "src" / "main" / "scala",
      swaggerClientCodeTargetDir := baseDirectory.value / "src" / "main" / "scala",
      swaggerServerRoutesFile := baseDirectory.value / "src" / "main" / "resources" / "routes",
      swaggerCodeGenPackage := "swagger.codegen",
      swaggerCodeProvidedPackage := "com.yourcompany",
      swaggerModelFilesSplitting := "singleFile",
      swaggerGenerateJsonRW := true,
      swaggerGenerateControllers := true,
      swaggerModelCodeGenClass := new DefaultModelGenerator(),
      swaggerJsonCodeGenClass := new DefaultJsonGenerator(),
      swaggerServerCodeGenClass := new DefaultServerGenerator(),
      swaggerClientCodeGenClass := new DefaultClientGenerator(),
      swaggerClean := {
        swaggerCleanImpl(
          codegenPackage = swaggerCodeGenPackage.value,
          modelTargetDir = swaggerModelCodeTargetDir.value.getAbsoluteFile,
          serverTargetDir = swaggerServerCodeTargetDir.value.getAbsoluteFile,
          routesFile = swaggerServerRoutesFile.value.getAbsoluteFile,
          clientTargetDir = swaggerClientCodeTargetDir.value.getAbsoluteFile
        )
      },
      swaggerCodeGen := {
        swaggerCodeGenImpl(
          codegenPackage = swaggerCodeGenPackage.value,
          sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
          fileSplittingMode = swaggerModelFilesSplitting.value,
          generateJson = swaggerGenerateJsonRW.value,
          targetDir = swaggerModelCodeTargetDir.value.getAbsoluteFile,
          modelGenerator = swaggerModelCodeGenClass.value,
          jsonGenerator = swaggerJsonCodeGenClass.value
        )
      },
      swaggerServerCodeGen := {
        swaggerServerCodeGenImpl(
          targetDir = swaggerServerCodeTargetDir.value.getAbsoluteFile,
          codegenPackage = swaggerCodeGenPackage.value,
          sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
          codeProvidedPackage = swaggerCodeProvidedPackage.value,
          targetRoutesFile = swaggerServerRoutesFile.value.getAbsoluteFile,
          generateControllers = swaggerGenerateControllers.value,
          serverGenerator = swaggerServerCodeGenClass.value
        )
      },
      swaggerClientCodeGen := {
        swaggerClientCodeGenImpl(
          codegenPackage = swaggerCodeGenPackage.value,
          sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
          targetDir = swaggerClientCodeTargetDir.value.getAbsoluteFile,
          clientGenerator = swaggerClientCodeGenClass.value
        )
      })
  }

  def swaggerCleanImpl(modelTargetDir: File,
                       serverTargetDir: File,
                       routesFile: File,
                       clientTargetDir: File,
                       codegenPackage: String) = {
    routesFile.delete()
    IO delete packageDir(modelTargetDir, codegenPackage)
    IO delete packageDir(serverTargetDir, codegenPackage)
    IO delete packageDir(clientTargetDir, codegenPackage)
  }

  def swaggerCodeGenImpl(codegenPackage: String,
                         sourcesDir: File,
                         fileSplittingMode: String,
                         generateJson: Boolean,
                         targetDir: File,
                         modelGenerator: ModelGenerator,
                         jsonGenerator: JsonGenerator) = {

    checkFileExistence(sourcesDir)

    val models: Map[String, Iterable[SyntaxString]] =
      (for {
        file <- sourcesDir.listFiles()
        fName = file.getName
        fPath = file.getAbsolutePath
        if fName.endsWith(".json") || fName.endsWith(".yaml")
      } yield {
        fName -> modelGenerator.generate(fPath, codegenPackage)
      }).toMap

    val jsonFormats: List[SyntaxString] =
      (for {
        file <- sourcesDir.listFiles()
        fName = file.getName
        fPath = file.getAbsolutePath
        if fName.endsWith(".json") || fName.endsWith(".yaml")
      } yield {
        jsonGenerator.generate(fPath, codegenPackage).toList
      }).flatten.toList

    val destDir = packageDir(targetDir, codegenPackage)

    def getFileName(s: String) =
      new String(s.toCharArray.takeWhile(_ == '.'))

    import FileSplittingModes._
    FileSplittingModes(fileSplittingMode) match {
      case SingleFile =>
        val code =
          models.values.flatten
            .flatMap(_.pre.split("\n"))
            .toList
            .distinct
            .mkString("\n") +
            models.values.flatten
              .map(_.code)
              .toList
              .distinct
              .mkString("\n\n", "\n\n", "\n")

        IO write (destDir / "Model.scala", code)
      case OneFilePerSource =>
        models.map {
          case (k, m) =>
            k ->
              (m.flatMap(_.pre.split("\n")).toList.distinct.mkString("\n") +
                m.map(_.code).toList.distinct.mkString("\n\n", "\n\n", "\n"))
        }.foreach {
          case (k, code) =>
            val name = getFileName(k) + ".scala"
            IO write (destDir / name, code)
        }
      case OneFilePerModel =>
        models.values.flatten.foreach { v =>
          val code =
            v.pre + "\n\n" + v.code
          val name = getFileName(v.name) + ".scala"
          IO write (destDir / name, code)
        }
    }

    if (generateJson) {
      val jsonDir = packageDir(destDir, "json")
      val code =
        jsonFormats.flatMap(_.pre.split("\n")).distinct.mkString("\n") +
          jsonFormats.map(_.code).mkString("\n\n", "\n\n", "\n")
      IO write (jsonDir / "package.scala", code)
    }
  }

  def swaggerServerCodeGenImpl(targetDir: File,
                               codegenPackage: String,
                               sourcesDir: File,
                               codeProvidedPackage: String,
                               targetRoutesFile: File,
                               generateControllers: Boolean,
                               serverGenerator: ServerGenerator) = {
    checkFileExistence(sourcesDir)

    val routes: String =
      (for {
        file <- sourcesDir.listFiles()
        fName = file.getName
        fPath = file.getAbsolutePath
        if fName.endsWith(".json") || fName.endsWith(".yaml")
      } yield {
        serverGenerator.generateRoutes(fPath, codegenPackage)
      }).flatten.mkString("\n")

    val sr =
      routes.split("\n").toList.distinct.mkString("\n", "\n\n", "\n")

    IO write (targetRoutesFile, sr)

    if (generateControllers) {
      val controllers: List[SyntaxString] =
        (for {
          file <- sourcesDir.listFiles()
          fName = file.getName
          fPath = file.getAbsolutePath
          if fName.endsWith(".json") || fName.endsWith(".yaml")
        } yield {
          serverGenerator.generate(fPath, codegenPackage, codeProvidedPackage)
        }).flatten.toList

      val destDir = packageDir(targetDir, codegenPackage + ".controller")

      controllers.foreach {
        case ss =>
          IO write (destDir / (ss.name + ".scala"), ss.pre + "\n\n" + ss.code)
      }
    }
  }

  def swaggerClientCodeGenImpl(codegenPackage: String,
                               sourcesDir: File,
                               targetDir: File,
                               clientGenerator: ClientGenerator) = {
    checkFileExistence(sourcesDir)

    val clients: List[SyntaxString] =
      (for {
        file <- sourcesDir.listFiles()
        fName = file.getName
        fPath = file.getAbsolutePath
        if fName.endsWith(".json") || fName.endsWith(".yaml")
      } yield {
        clientGenerator.generate(fPath, codegenPackage)
      }).flatten.toList

    val destDir = packageDir(targetDir, codegenPackage + ".client")

    clients.foreach {
      case ss =>
        IO write (destDir / (ss.name + ".scala"), ss.pre + "\n\n" + ss.code)
    }
  }

  def checkFileExistence(sDir: File) = {
    if (!sDir.exists() || !sDir.isDirectory)
      throw new Exception(s"Provided swagger source dir $sDir doesn't exists")
    else if (sDir
               .listFiles()
               .count(x =>
                 x.getName.endsWith(".json") || x.getName
                   .endsWith(".yaml")) < 1)
      throw new Exception(s"There are no files in swagger directory $sDir")
  }

  def packageDir(base: File, packageName: String): File =
    base / packageName.replace(".", File.separator)
}
