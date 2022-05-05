/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.models

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Random.nextInt

object Helper extends App {

  def randomly_capitalize(str: String): String  = {
    str.map(char => nextInt() % 2 == 0 match {
      case true => char.toUpper
      case false => char.toLower
    })
  }

  def generate_valid_strings(strs: Seq[String]): List[String] = {
    Range(0, 50).flatMap(_ => strs
      .map(rType => randomly_capitalize(rType)))
      .toList
  }
}

class RepoTypeSpec extends AnyWordSpec with Matchers {
  val repoTypeStrings:Seq[String] = RepoType.values.map(rType => rType.asString)
  val valid_inputs: List[String] = Helper.generate_valid_strings(repoTypeStrings) //Generate 50 capitalization variations of each RepoType

  "repoType.parse" when {
    "parsing service with various capitalization styles" must {
      "returns a RepoType object" in {
        valid_inputs
          .foreach(i => RepoType.parse(i) match {
            case Right(value) => RepoType.values must contain (value)
            case _            => fail(s" The input $i has no right value!")
          })
      }
    }
  }

  val invalid_inputs = Seq("services", "foo", "42", "HELLO", "WORLD", "", ";.]'")

  "repoType.parse" when {
    "parsing invalid query parameter inputs" must {
      "return an error message" in {
        invalid_inputs
          .foreach(i => RepoType.parse(i).isLeft mustBe true)
      }
    }
  }

}


