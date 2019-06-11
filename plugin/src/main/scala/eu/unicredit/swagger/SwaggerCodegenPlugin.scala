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

    val swaggerRoutesCodeGen =
      taskKey[Seq[File]]("Generate swagger server routes")

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
      // should generate SRID code in src_managed, see issue #31
      //resourceGenerators in Compile += Def.task { swaggerRoutesCodeGen.value }.taskValue,
      swaggerSourcesDir := (sourceDirectory in Compile).value / "swagger",
      swaggerModelCodeTargetDir := (sourceManaged in Compile).value / "swagger" / "model",
      swaggerServerCodeTargetDir := (sourceManaged in Compile).value / "swagger" / "server",
      swaggerClientCodeTargetDir := (sourceManaged in Compile).value / "swagger" / "client",
      swaggerServerRoutesFile := (resourceDirectory in Compile).value / "routes",
      swaggerCodeGenPackage := "swagger.codegen",
      swaggerCodeProvidedPackage := "com.yourcompany",
      swaggerModelFilesSplitting := "singleFile",
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
          routesFile = swaggerServerRoutesFile.value.getAbsoluteFile,
          clientTargetDir = swaggerClientCodeTargetDir.value.getAbsoluteFile,
          deleteRoutes = swaggerGenerateServer.value
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
      swaggerRoutesCodeGen := {
        swaggerRoutesCodeGenImpl(
          codegenPackage = swaggerCodeGenPackage.value,
          sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
          targetRoutesFile = swaggerServerRoutesFile.value.getAbsoluteFile,
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
                       routesFile: File,
                       clientTargetDir: File,
                       codegenPackage: String,
                       deleteRoutes: Boolean): Unit = {
    if (deleteRoutes) routesFile.delete()
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
          Some(fName -> modelGenerator.generate(fPath, codegenPackage))
        } catch {
          case e: Exception =>
            logger.warn(s"Invalid swagger format: ${e.getMessage} - ${file.getCanonicalPath}")
            None
        }
      }).flatten.toMap

    val destDir = packageDir(targetDir, codegenPackage)

    import FileSplittingModes._
    FileSplittingModes(fileSplittingMode) match {
      case SingleFile =>
        val ss = SyntaxCode("Model.scala",
          models.values.flatten.head.pkg,
          models.values.flatten.flatMap(_.imports).toList,
          models.values.flatten.flatMap(_.statements).toList)

        IO write (destDir / ss.name, ss.code)
      case OneFilePerSource =>
        models.foreach { case (fileName, model) =>
          val name = fileName.split(".yaml$|.json$").head.capitalize
          val ss = SyntaxCode(name + ".scala",
            model.head.pkg,
            model.flatMap(_.imports).toList,
            model.flatMap(_.statements).toList)

          IO write (destDir / ss.name, ss.code)
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
            jsonGenerator.generate(fPath, codegenPackage).toList
          } catch {
            case e: Exception =>
              logger.warn(s"Invalid swagger format: ${e.getMessage} - ${file.getCanonicalPath}")
              List.empty
          }
        }).flatten

      jsonFormats.foreach { ss =>
        val jsonDir = packageDir(destDir, ss.name)
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

    val controllers =
      (for {
        file <- sourcesDir.listFiles()
        fName = file.getName
        fPath = file.getAbsolutePath
        if fName.endsWith(".json") || fName.endsWith(".yaml")
      } yield {
        try {
          serverGenerator.generate(fPath, codegenPackage, codeProvidedPackage)
        } catch {
          case e: Exception =>
            logger.warn(s"Invalid swagger format: ${e.getMessage} - ${file.getCanonicalPath}")
            Iterable.empty
        }

      }).flatten

    val destDir = packageDir(targetDir, codegenPackage + ".controller")

    controllers.foreach { ss =>
      IO write (destDir / ss.name, ss.code)
    }

    (destDir ** -DirectoryFilter).get
  }

  def swaggerRoutesCodeGenImpl(codegenPackage: String,
                               sourcesDir: File,
                               targetRoutesFile: File,
                               serverGenerator: ServerGenerator,
                               logger: Logger): Seq[File] = {
    checkFileExistence(sourcesDir)
    IO delete targetRoutesFile

    val routes =
      (for {
        file <- sourcesDir.listFiles()
        fName = file.getName
        fPath = file.getAbsolutePath
        if fName.endsWith(".json") || fName.endsWith(".yaml")
      } yield {
        try {
          serverGenerator.generateRoutes(fPath, codegenPackage)
        } catch {
          case e: Exception =>
            logger.warn(s"Invalid swagger format: ${e.getMessage} - ${file.getCanonicalPath}")
            None
        }
      }).flatten

    val sr = routes.distinct.mkString("# Generated by sbt-swagger-codegen\n", "\n", "\n")

    IO write (targetRoutesFile, sr)

    Seq(targetRoutesFile)
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
          clientGenerator.generate(fPath, codegenPackage)
        } catch {
          case e: Exception =>
            logger.warn(s"Invalid swagger format: ${e.getMessage} - ${file.getCanonicalPath}")
            Iterable.empty
        }
      }).flatten

    val destDir = packageDir(targetDir, codegenPackage + ".client")

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
}
