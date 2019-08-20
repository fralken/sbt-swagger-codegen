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
package eu.unicredit.swagger.generators

import eu.unicredit.swagger.SwaggerConverters
import io.swagger.models._

import scala.meta._

trait SharedServerClientCode extends SwaggerConverters {
  import java.io.File.separator
  import java.io.File.separatorChar

  def nameFromFileName(fn: String) = {
    val sep = if (separatorChar == 92.toChar) "\\\\" else separator
    fn.split(sep)
      .toList
      .last
      .replace(".yaml", "")
      .replace(".json", "")
  }

  def objectNameFromFileName(fn: String, obj: String) = {
    nameFromFileName(fn).capitalize + obj
  }

  def getOperations(path: Path): List[(String, Operation)] = {
    List(
      Option(path.getDelete) map ("DELETE" -> _),
      Option(path.getGet) map ("GET" -> _),
      Option(path.getPost) map ("POST" -> _),
      Option(path.getPut) map ("PUT" -> _)
    ).flatten
  }

  def getResponseResultsAndTypes(op: Operation): Option[(Term, Option[Type])] =
    respTypeMap.flatMap {
      case (k, v) =>
        Option(op.getResponses get k) map { response =>
          Term.Name(v) -> getResponseType(response)
        }
    }.headOption

  private val respTypeMap: Seq[(String, String)] =
    Seq(
      "200" -> "Ok",
      "201" -> "Created",
      "202" -> "Accepted",
      "203" -> "NonAuthoritativeInformation",
      "204" -> "NoContent",
      "205" -> "ResetContent",
      "206" -> "PartialContent",
      "207" -> "MultiStatus",
      "default" -> "Ok"
    )
}
