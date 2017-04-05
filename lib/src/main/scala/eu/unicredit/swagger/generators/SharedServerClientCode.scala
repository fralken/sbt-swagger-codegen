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

import eu.unicredit.swagger.SwaggerConversion
import eu.unicredit.swagger.StringUtils._

import treehugger.forest._
import treehuggerDSL._

import io.swagger.models._
import io.swagger.models.parameters._

trait SharedServerClientCode extends SwaggerConversion {
  import java.io.File.separator
  import java.io.File.separatorChar

  def objectNameFromFileName(fn: String, obj: String) = {
    val sep = if (separatorChar == 92.toChar) "\\\\" else separator
    fn.split(sep)
      .toList
      .last
      .replace(".yaml", "")
      .replace(".json", "")
      .capitalize + obj
  }

  def genMethodCall(className: String, methodName: String, params: Seq[Parameter]): String = {
    val p = getMethodParamas(params).map {
      case (n, v) => s"$n: ${treeToString(v.tpt)}"
    }
    // since it is a route definition, this is not Scala code, so we generate it manually
    s"$className.$methodName" + p.mkString("(", ", ", ")")
  }

  def getMethodParamas(params: Seq[Parameter]): Map[String, ValDef] =
    params
      .filter {
        case _: PathParameter => true
        case _: QueryParameter => true
        case _: HeaderParameter => true
        case _: BodyParameter => false
        case x =>
          println(
            s"unmanaged parameter type for parameter ${x.getName}, please contact the developer to implement it XD")
          false
      }
      .sortBy { //the order must be verified...
        case _: HeaderParameter => 1
        case _: PathParameter => 2
        case _: QueryParameter => 3
        // other subtypes have been removed already
      }
      .map(p => {
        (p.getName, PARAM(p.getName, paramType(p)): ValDef)
      })
      .toMap

  def getOkRespType(op: Operation): Option[(String, Option[Type])] =
    respTypeMap.flatMap {
      case (k, v) =>
        Option(op.getResponses get k) map { response =>
          v -> Option(response.getSchema).map(noOptPropType)
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
