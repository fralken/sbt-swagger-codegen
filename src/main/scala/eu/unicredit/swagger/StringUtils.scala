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

trait StringUtils {

  def sanitizePath(s: String, replaceChar: Char) =
    s.toCharArray.foldLeft(("", false))((old, nc) => {
      if (old._2 && nc != '}') (old._1 + nc, old._2)
      else if (old._2 && nc == '}') (old._1, false)
      else if (!old._2 && nc == '{') (old._1 + replaceChar, true)
      else (old._1 + nc, false)
    })._1.trim()

  def cleanDuplicateSlash(s: String) =
    s.toCharArray.foldLeft("")((old, nc) => {
      if (nc == '/' && old.endsWith("/")) old
      else old + nc
    })

  def cleanUrl(s: String) = {
    val str =
      s.replace("/?", "?")

    if (str.endsWith("/"))
      str.substring(0, str.length() - 1)
    else
      str
  }

  def cleanPathParams(s: String) =
    s.toCharArray.foldLeft("")((old, nc) => {
      if (nc == ':') old + '$'
      else old + nc
    }).trim()

  def empty(n: Int): String =
    new String((for (i <- 1 to n) yield ' ').toArray)

  def trimTo(n: Int, s: String): String =
    new String(empty(n).zipAll(s, ' ', ' ').map(_._2).toArray)

  def doUrl(basePath: String, path: String) = {
    cleanUrl(
      cleanDuplicateSlash(
        basePath + sanitizePath(path, ':')))
  }

}
