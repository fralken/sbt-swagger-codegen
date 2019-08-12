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

  sealed trait FileSplittingMode
  object FileSplittingMode {
    case object SingleFile extends FileSplittingMode
    case object OneFilePerSource extends FileSplittingMode
    case object OneFilePerModel extends FileSplittingMode

    def apply(s: String): FileSplittingMode =
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
      taskKey[Seq[File]]("Generate swagger models and json converters")

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
        Def.taskDyn {
          swaggerModelCodeGenCachedImpl(
            codegenPackage = swaggerCodeGenPackage.value,
            sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
            fileSplittingMode = swaggerModelFilesSplitting.value,
            generateJson = swaggerGenerateJsonRW.value,
            targetDir = swaggerModelCodeTargetDir.value.getAbsoluteFile,
            modelGenerator = swaggerModelCodeGenClass.value,
            jsonGenerator = swaggerJsonCodeGenClass.value,
            key = swaggerModelCodeGen
          )
        }.value
      },
      swaggerServerCodeGen := {
        Def.taskDyn {
          swaggerServerCodeGenCachedImpl(
            targetDir = swaggerServerCodeTargetDir.value.getAbsoluteFile,
            codegenPackage = swaggerCodeGenPackage.value,
            sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
            codeProvidedPackage = swaggerCodeProvidedPackage.value,
            serverGenerator = swaggerServerCodeGenClass.value,
            key = swaggerServerCodeGen
          )
        }.value
      },
      swaggerRoutesCodeGen := {
        Def.taskDyn {
          swaggerRoutesCodeGenCachedImpl(
            codegenPackage = swaggerCodeGenPackage.value,
            sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
            targetRoutesFile = swaggerServerRoutesFile.value.getAbsoluteFile,
            serverGenerator = swaggerServerCodeGenClass.value,
            key = swaggerRoutesCodeGen
          )
        }.value
      },
      swaggerClientCodeGen := {
        Def.taskDyn {
          swaggerClientCodeGenCachedImpl(
            codegenPackage = swaggerCodeGenPackage.value,
            sourcesDir = swaggerSourcesDir.value.getAbsoluteFile,
            targetDir = swaggerClientCodeTargetDir.value.getAbsoluteFile,
            clientGenerator = swaggerClientCodeGenClass.value,
            key = swaggerClientCodeGen
          )
        }.value
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

  def swaggerModelCodeGenCachedImpl(codegenPackage: String,
                                    sourcesDir: File,
                                    fileSplittingMode: String,
                                    generateJson: Boolean,
                                    targetDir: File,
                                    modelGenerator: ModelGenerator,
                                    jsonGenerator: JsonGenerator,
                                    key: TaskKey[Seq[File]]): Def.Initialize[Task[Seq[File]]] =
    cachedGenerate(
      key = key,
      taskName = "Models",
      sourcesDir = sourcesDir,
      targetDir = targetDir,
      generate = swaggerFiles =>
        swaggerModelCodeGenImpl(codegenPackage, swaggerFiles, fileSplittingMode, generateJson, targetDir, modelGenerator, jsonGenerator)
    )

  def swaggerModelCodeGenImpl(codegenPackage: String,
                              swaggerFiles: Set[File],
                              fileSplittingMode: String,
                              generateJson: Boolean,
                              targetDir: File,
                              modelGenerator: ModelGenerator,
                              jsonGenerator: JsonGenerator): Seq[File] = {

    val models: Map[String, Iterable[SyntaxString]] =
      swaggerFiles.map(file => file.getName -> modelGenerator.generate(file.getAbsolutePath, codegenPackage)).toMap

    val destDir = packageDir(targetDir, codegenPackage)

    import FileSplittingMode._
    FileSplittingMode(fileSplittingMode) match {
      case SingleFile =>
        val ss = SyntaxString("Model.scala",
          models.values.flatten.flatMap(_.pre.split("\n")).toList.distinct.mkString("\n"),
          models.values.flatten.map(_.impl).toList.distinct.mkString("\n"))

        IO write (destDir / ss.name, ss.code)
      case OneFilePerSource =>
        models.foreach { case (k, m) =>
          val name = k.split(".yaml$|.json$").head.capitalize
          val ss = SyntaxString(name + ".scala",
            m.flatMap(_.pre.split("\n")).toList.distinct.mkString("\n"),
            m.map(_.impl).toList.distinct.mkString("\n"))

          IO write (destDir / ss.name, ss.code)
        }
      case OneFilePerModel =>
        models.values.flatten.foreach { ss =>
          IO write (destDir / ss.name, ss.code)
        }
    }

    if (generateJson) {
      val jsonFormats =
        swaggerFiles.flatMap(file => jsonGenerator.generate(file.getAbsolutePath, codegenPackage).toList)

      jsonFormats.foreach { ss =>
        val jsonDir = packageDir(destDir, ss.name)
        IO write (jsonDir / "package.scala", ss.code)
      }
    }

    (destDir ** -DirectoryFilter).get
  }

  def swaggerServerCodeGenCachedImpl(targetDir: File,
                                     codegenPackage: String,
                                     sourcesDir: File,
                                     codeProvidedPackage: String,
                                     serverGenerator: ServerGenerator,
                                     key: TaskKey[Seq[File]]): Def.Initialize[Task[Seq[File]]] =
    cachedGenerate(
      key = key,
      taskName = "Server",
      sourcesDir = sourcesDir,
      targetDir = targetDir,
      generate = swaggerFiles => swaggerServerCodeGenImpl(targetDir, codegenPackage, swaggerFiles, codeProvidedPackage, serverGenerator)
    )

  def swaggerServerCodeGenImpl(targetDir: File,
                               codegenPackage: String,
                               swaggerFiles: Set[File],
                               codeProvidedPackage: String,
                               serverGenerator: ServerGenerator): Seq[File] = {

    val controllers =
      swaggerFiles.flatMap(f => serverGenerator.generate(f.getAbsolutePath, codegenPackage, codeProvidedPackage))

    val destDir = packageDir(targetDir, codegenPackage + ".controller")

    controllers.foreach { ss =>
      IO write (destDir / ss.name, ss.code)
    }

    (destDir ** -DirectoryFilter).get
  }

  def swaggerRoutesCodeGenCachedImpl(codegenPackage: String,
                                     sourcesDir: File,
                                     targetRoutesFile: File,
                                     serverGenerator: ServerGenerator,
                                     key: TaskKey[Seq[File]]): Def.Initialize[Task[Seq[File]]] =
    cachedGenerate(
      key = key,
      taskName = "Routes",
      sourcesDir = sourcesDir,
      targetDir = targetRoutesFile,
      generate = swaggerFiles => swaggerRoutesCodeGenImpl(codegenPackage, swaggerFiles, targetRoutesFile, serverGenerator)
    )

  def swaggerRoutesCodeGenImpl(codegenPackage: String,
                               swaggerFiles: Set[File],
                               targetRoutesFile: File,
                               serverGenerator: ServerGenerator): Seq[File] = {

    val routes =
      swaggerFiles.flatMap(f => serverGenerator.generateRoutes(f.getAbsolutePath, codegenPackage))

    val sr = routes.mkString("\n", "\n\n", "\n")

    IO write (targetRoutesFile, sr)

    Seq(targetRoutesFile)
  }

  def swaggerClientCodeGenCachedImpl(codegenPackage: String,
                                     sourcesDir: File,
                                     targetDir: File,
                                     clientGenerator: ClientGenerator,
                                     key: TaskKey[Seq[File]] ): Def.Initialize[Task[Seq[File]]] =
    cachedGenerate(
      key = key,
      taskName = "Client",
      sourcesDir = sourcesDir,
      targetDir = targetDir,
      generate = swaggerFiles => swaggerClientCodeGenImpl(codegenPackage, swaggerFiles, targetDir, clientGenerator)
    )

  def swaggerClientCodeGenImpl(codegenPackage: String,
                               swaggerFiles: Set[File],
                               targetDir: File,
                               clientGenerator: ClientGenerator): Seq[File] = {

    val clients = swaggerFiles.flatMap(f => clientGenerator.generate(f.getAbsolutePath, codegenPackage))

    val destDir = packageDir(targetDir, codegenPackage + ".client")

    clients.foreach { ss =>
      IO write (destDir / ss.name, ss.code)
    }

    (destDir ** -DirectoryFilter).get
  }

  private def cachedGenerate(key: TaskKey[Seq[File]],
                             taskName: String,
                             sourcesDir: File,
                             targetDir: File,
                             generate: Set[File] => Seq[File]): Def.Initialize[Task[Seq[File]]] = {


    Def.task {
      val log = streams.value.log

      checkFileExistence(sourcesDir)
      val swaggerFiles = sourcesDir.listFiles().filter(isSwaggerFile).toSet

      val cacheFile = (streams in key).value.cacheDirectory / s"swagger_${taskName.toLowerCase}_${scalaBinaryVersion.value}"
      val cachedGenerate: Set[File] => Set[File] = FileFunction.cached(
        cacheFile,
        inStyle = FilesInfo.lastModified,
        outStyle = FilesInfo.exists
      ) { _ =>

        logTask(log, swaggerFiles.size, targetDir, taskName)

        IO delete targetDir
        generate(swaggerFiles).toSet
      }

      cachedGenerate(swaggerFiles).toSeq
    }

  }

  def checkFileExistence(sDir: File): Unit = {
    if (!sDir.exists() || !sDir.isDirectory)
      throw new Exception(s"Provided swagger source dir $sDir doesn't exists")
    else if (sDir.listFiles().count(isSwaggerFile) < 1)
      throw new Exception(s"There are no files in swagger directory $sDir")
  }

  private def isSwaggerFile(f: File): Boolean =
    f.getName.endsWith(".json") || f.getName.endsWith(".yaml")

  private def logTask(log: Logger, nFiles: Int, targetDir: File, taskName: String): Unit =
    log.info(s"Compiling $nFiles swagger files ($taskName) to ${targetDir.getAbsolutePath}")

  def packageDir(base: File, packageName: String): File =
    base / packageName.replace(".", File.separator)
}
