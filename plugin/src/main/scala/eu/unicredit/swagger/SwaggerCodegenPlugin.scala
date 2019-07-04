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
import java.io.File.{separator, separatorChar}

import sbt._
import Keys._
import eu.unicredit.swagger.generators._

object SwaggerCodegenPlugin extends AutoPlugin {

  object FileSplittingModes {
    case object OneFilePerSource
    case object OneFilePerModel

    def apply(s: String) =
      s match {
        case "oneFilePerSource" => OneFilePerSource
        case "oneFilePerModel" => OneFilePerModel
        case any =>
          throw new Exception(
            s"Unsupported swaggerModelFileSplitting option $any please choose one of (oneFilePerSource | oneFilePerModel)")
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

    val swaggerCodeGenPackage = settingKey[String]("swaggerCodeGenPackage")

    val swaggerModelFilesSplitting =
      settingKey[String]("swaggerModelFileSplitting")

    val swaggerGenerateModel = settingKey[Boolean]("swaggerGenerateModel")
    val swaggerGenerateClient = settingKey[Boolean]("swaggerGenerateClient")
    val swaggerGenerateServer = settingKey[Boolean]("swaggerGenerateServer")
    val swaggerGenerateJsonRW = settingKey[Boolean]("swaggerGenerateJsonRW")

    val swaggerCodeProvidedPackage =
      settingKey[String]("swaggerCodeProvidedPackage")

    /*
     * Tasks
     */
    val swaggerClean = taskKey[Unit]("Clean swagger generated packages")

    val swaggerModelCodeGen =
      taskKey[Seq[File]]("Generate swagger models and JSON converters")

    val swaggerServerCodeGen =
      taskKey[Seq[File]]("Generate swagger server controllers boilerplate")

    val swaggerClientCodeGen = taskKey[Seq[File]]("Generate swagger client class with WS calls to specific routes")

  }

  import autoImport._
  override val requires: Plugins = plugins.JvmPlugin
  override def trigger = noTrigger

  private val modelDyn =
    Def.taskDyn {
      if (swaggerGenerateModel.value) Def.task { swaggerModelCodeGen.value } else
        Def.task { Seq.empty[File] }
    }
  private val clientDyn =
    Def.taskDyn {
      if (swaggerGenerateClient.value) Def.task { swaggerClientCodeGen.value } else
        Def.task { Seq.empty[File] }
    }
  private val serverDyn =
    Def.taskDyn {
      if (swaggerGenerateServer.value) Def.task { swaggerServerCodeGen.value } else
        Def.task { Seq.empty[File] }
    }

  override val projectSettings = {
    Seq(
      watchSources ++= swaggerSourcesDir.value.**("*.scala").get,
      sourceGenerators in Compile += Def.task {
        modelDyn.value ++ clientDyn.value ++ serverDyn.value
      }.taskValue,
      swaggerSourcesDir := (sourceDirectory in Compile).value / "swagger",
      swaggerModelCodeTargetDir := (sourceManaged in Compile).value / "swagger" / "model",
      swaggerServerCodeTargetDir := (sourceManaged in Compile).value / "swagger" / "server",
      swaggerClientCodeTargetDir := (sourceManaged in Compile).value / "swagger" / "client",
      swaggerCodeGenPackage := "swagger.codegen",
      swaggerCodeProvidedPackage := "com.yourcompany",
      swaggerModelFilesSplitting := "oneFilePerSource",
      swaggerGenerateModel := true,
      swaggerGenerateClient := false,
      swaggerGenerateServer := false,
      swaggerGenerateJsonRW := true,
      swaggerModelCodeGenClass := new DefaultModelGenerator(),
      swaggerJsonCodeGenClass := new DefaultJsonGenerator(),
      swaggerServerCodeGenClass := new DefaultServerGenerator(),
      swaggerClientCodeGenClass := new DefaultClientGenerator(),
      swaggerClean := {
        swaggerCleanImpl(
          codegenPackage = swaggerCodeGenPackage.value,
          modelTargetDir = swaggerModelCodeTargetDir.value.getAbsoluteFile,
          serverTargetDir = swaggerServerCodeTargetDir.value.getAbsoluteFile,
          clientTargetDir = swaggerClientCodeTargetDir.value.getAbsoluteFile
        )
      },
      swaggerModelCodeGen := {
        swaggerModelCodeGenImpl(
          codegenPackage = swaggerCodeGenPackage.value,
          sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
          fileSplittingMode = swaggerModelFilesSplitting.value,
          generateJson = swaggerGenerateJsonRW.value,
          targetDir = swaggerModelCodeTargetDir.value.getAbsoluteFile,
          modelGenerator = swaggerModelCodeGenClass.value,
          jsonGenerator = swaggerJsonCodeGenClass.value,
          logger = sLog.value
        )
      },
      swaggerServerCodeGen := {
        swaggerServerCodeGenImpl(
          targetDir = swaggerServerCodeTargetDir.value.getAbsoluteFile,
          codegenPackage = swaggerCodeGenPackage.value,
          sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
          codeProvidedPackage = swaggerCodeProvidedPackage.value,
          serverGenerator = swaggerServerCodeGenClass.value,
          logger = sLog.value
        )
      },
      swaggerClientCodeGen := {
        swaggerClientCodeGenImpl(
          codegenPackage = swaggerCodeGenPackage.value,
          sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
          targetDir = swaggerClientCodeTargetDir.value.getAbsoluteFile,
          clientGenerator = swaggerClientCodeGenClass.value,
          logger = sLog.value
        )
      }
    )
  }

  def swaggerCleanImpl(modelTargetDir: File,
                       serverTargetDir: File,
                       clientTargetDir: File,
                       codegenPackage: String): Unit = {
    IO delete packageDir(modelTargetDir, codegenPackage)
    IO delete packageDir(serverTargetDir, codegenPackage)
    IO delete packageDir(clientTargetDir, codegenPackage)
  }

  def swaggerModelCodeGenImpl(codegenPackage: String,
                              sourcesDir: File,
                              fileSplittingMode: String,
                              generateJson: Boolean,
                              targetDir: File,
                              modelGenerator: ModelGenerator,
                              jsonGenerator: JsonGenerator,
                              logger: Logger): Seq[File] = {

    checkFileExistence(sourcesDir)
    IO delete targetDir

    val models: Map[String, Iterable[SyntaxCode]] =
      (for {
        file <- sourcesDir.listFiles()
        fName = file.getName
        fPath = file.getAbsolutePath
        if fName.endsWith(".json") || fName.endsWith(".yaml")
      } yield {
        try {
          nameFromFileName(fName) -> modelGenerator.generate(fPath, s"$codegenPackage.${packageNameFromFileName(fName)}")
        } catch {
          case e: Exception =>
            logger.error(s"Invalid swagger format: ${e.getMessage} - ${file.getCanonicalPath}")
            throw e
        }
      }).toMap

    val destDir = packageDir(targetDir, codegenPackage)

    import FileSplittingModes._
    FileSplittingModes(fileSplittingMode) match {
      case OneFilePerSource =>
        models.foreach { case (name, model) =>
          if (model.nonEmpty) {
            val ss = SyntaxCode(name + "Models.scala",
              model.head.pkg,
              model.flatMap(_.imports).toList,
              model.flatMap(_.statements).toList)

            IO write (destDir / ss.name, ss.code)
          }
        }
      case OneFilePerModel =>
        models.values.flatten.foreach { ss =>
          IO write (destDir / ss.name, ss.code)
        }
    }

    if (generateJson) {
      val jsonFormats =
        (for {
          file <- sourcesDir.listFiles()
          fName = file.getName
          fPath = file.getAbsolutePath
          if fName.endsWith(".json") || fName.endsWith(".yaml")
        } yield {
          try {
            val packageName = packageNameFromFileName(fName)
            jsonGenerator.generate(fPath, s"$codegenPackage.$packageName").toList.map { j => (packageName, j)}
          } catch {
            case e: Exception =>
              logger.error(s"Invalid swagger format: ${e.getMessage} - ${file.getCanonicalPath}")
              throw e
          }
        }).flatten

      jsonFormats.foreach { case (packageName, ss) =>
        val jsonDir = packageDir(destDir / packageName, ss.name)
        IO write (jsonDir / "package.scala", ss.code)
      }
    }

    (destDir ** -DirectoryFilter).get
  }

  def swaggerServerCodeGenImpl(targetDir: File,
                               codegenPackage: String,
                               sourcesDir: File,
                               codeProvidedPackage: String,
                               serverGenerator: ServerGenerator,
                               logger: Logger): Seq[File] = {
    checkFileExistence(sourcesDir)
    IO delete targetDir

    val servers =
      (for {
        file <- sourcesDir.listFiles()
        fName = file.getName
        fPath = file.getAbsolutePath
        if fName.endsWith(".json") || fName.endsWith(".yaml")
      } yield {
        try {
          serverGenerator.generate(fPath, s"$codegenPackage.${packageNameFromFileName(fName)}", codeProvidedPackage)
        } catch {
          case e: Exception =>
            logger.error(s"Invalid swagger format: ${e.getMessage} - ${file.getCanonicalPath}")
            throw e
        }

      }).flatten

    val destDir = packageDir(targetDir, codegenPackage)

    servers.foreach { ss =>
      IO write (destDir / ss.name, ss.code)
    }

    (destDir ** -DirectoryFilter).get
  }

  def swaggerClientCodeGenImpl(codegenPackage: String,
                               sourcesDir: File,
                               targetDir: File,
                               clientGenerator: ClientGenerator,
                               logger: Logger): Seq[File] = {
    checkFileExistence(sourcesDir)
    IO delete targetDir

    val clients =
      (for {
        file <- sourcesDir.listFiles()
        fName = file.getName
        fPath = file.getAbsolutePath
        if fName.endsWith(".json") || fName.endsWith(".yaml")
      } yield {
        try {
          clientGenerator.generate(fPath, s"$codegenPackage.${packageNameFromFileName(fName)}")
        } catch {
          case e: Exception =>
            logger.error(s"Invalid swagger format: ${e.getMessage} - ${file.getCanonicalPath}")
            throw e
        }
      }).flatten

    val destDir = packageDir(targetDir, codegenPackage)

    clients.foreach { ss =>
      IO write (destDir / ss.name, ss.code)
    }

    (destDir ** -DirectoryFilter).get
  }

  def checkFileExistence(sDir: File) = {
    if (!sDir.exists() || !sDir.isDirectory)
      throw new Exception(s"Provided swagger source dir $sDir doesn't exists")
    else if (sDir.listFiles().count(x => x.getName.endsWith(".json") || x.getName.endsWith(".yaml")) < 1)
      throw new Exception(s"There are no files in swagger directory $sDir")
  }

  def packageDir(base: File, packageName: String): File =
    base / packageName.replace(".", File.separator)

  def nameFromFileName(fn: String) = {
    val sep = if (separatorChar == 92.toChar) "\\\\" else separator
    fn.split(sep)
      .toList
      .last
      .replace(".yaml", "")
      .replace(".json", "")
      .split("-")
      .map(_.capitalize)
      .mkString
  }

  def packageNameFromFileName(fn: String) = nameFromFileName(fn).toLowerCase
}
