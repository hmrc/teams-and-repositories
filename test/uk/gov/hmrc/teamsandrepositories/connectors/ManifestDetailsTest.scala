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

package uk.gov.hmrc.teamsandrepositories.connectors

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.ManifestDetails
import uk.gov.hmrc.teamsandrepositories.models.RepoType

class ManifestDetailsTest extends AnyWordSpecLike with Matchers {

  "ManifestDetails.Parse" must {

    "parse deprecated flag and reason" in {
      val manifest =
        """
          |repoVisibility: public_0C3F0CE3E6E6448FAD341E7BFA50FCD333E06A20CFF05FCACE61154DDBBADF71
          |type: library
          |deprecated: true
          |""".stripMargin
      val details = ManifestDetails.parse("repo1", manifest)

      details.isDefined             mustBe true
      details.get.repoType          mustBe Some(RepoType.Library)
      details.get.isDeprecated        mustBe true

    }

    "parse default to not deprecated" in {
      val manifest =
        """
          |repoVisibility: public_0C3F0CE3E6E6448FAD341E7BFA50FCD333E06A20CFF05FCACE61154DDBBADF71
          |type: library
          |""".stripMargin
      val details = ManifestDetails.parse("repo1", manifest)

      details.isDefined             mustBe true
      details.get.repoType          mustBe Some(RepoType.Library)
      details.get.isDeprecated        mustBe false
    }

  }

}
