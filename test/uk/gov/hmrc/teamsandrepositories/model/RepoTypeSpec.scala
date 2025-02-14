/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.teamsandrepositories.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.util.Parser

import scala.util.Random.nextInt

object Helper:

  def randomlyCapitalize(str: String): String  =
    str.map(char => if nextInt() % 2 == 0 then char.toUpper else char.toLower)

  def generateValidStrings(strs: Seq[String]): List[String] =
    Range(0, 50).flatMap(_ => strs
      .map(rType => randomlyCapitalize(rType)))
      .toList

class RepoTypeSpec extends AnyWordSpec with Matchers:
  val repoTypeStrings: Seq[String] = RepoType.values.toIndexedSeq.map(rType => rType.asString)
  val valid_inputs: List[String] = Helper.generateValidStrings(repoTypeStrings) //Generate 50 capitalization variations of each RepoType

  "repoType.parse" when:
    "parsing service with various capitalization styles" should:
      "returns a RepoType object" in:
        valid_inputs
          .foreach(i => Parser[RepoType].parse(i) match
            case Right(value) => RepoType.values should contain (value)
            case _            => fail(s" The input $i has no right value!")
          )

  val invalid_inputs: Seq[String] =
    Seq("services", "foo", "42", "HELLO", "WORLD", "", ";.]'")

  "repoType.parse" when:
    "parsing invalid query parameter inputs" should:
      "return an error message" in:
        invalid_inputs
          .foreach(i => Parser[RepoType].parse(i).isLeft shouldBe true)
