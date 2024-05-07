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

package uk.gov.hmrc.teamsandrepositories.connectors

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.ManifestDetails
import uk.gov.hmrc.teamsandrepositories.models.{RepoType, ServiceType, Tag}

import java.time.Instant

class ManifestDetailsTest extends AnyWordSpecLike with Matchers {

  "ManifestDetails.Parse" should {

    "parse deprecated flag and reason" in {
      val manifest =
        """
          |repoVisibility: public_0C3F0CE3E6E6448FAD341E7BFA50FCD333E06A20CFF05FCACE61154DDBBADF71
          |type: library
          |deprecated: true
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined        shouldBe true
      details.get.repoType     shouldBe Some(RepoType.Library)
      details.get.isDeprecated shouldBe true
    }

    "parse description" in {
      val manifest =
        """
          |description: test description
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined shouldBe true
      details.get.description shouldBe Some("test description")
    }

    "parse end-of-life-date" in {
      val manifest =
        """
          |end-of-life-date: 2024-05-09
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined shouldBe true
      details.get.endOfLifeDate shouldBe Some(Instant.parse("2024-05-09T00:00:00Z"))
    }

    "parse end-of-life-date as None if invalid format" in {
      val manifest =
        """
          |end-of-life-date: 20000-05-09
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined shouldBe true
      details.get.endOfLifeDate shouldBe None
    }

    "parse end-of-life-date as None if key is missing" in {
      val manifest =
        """
          |repoVisibility: public_0C3F0CE3E6E6448FAD341E7BFA50FCD333E06A20CFF05FCACE61154DDBBADF71
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined shouldBe true
      details.get.endOfLifeDate shouldBe None
    }

    "parse default to not deprecated" in {
      val manifest =
        """
          |repoVisibility: public_0C3F0CE3E6E6448FAD341E7BFA50FCD333E06A20CFF05FCACE61154DDBBADF71
          |type: library
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined        shouldBe true
      details.get.repoType     shouldBe Some(RepoType.Library)
      details.get.isDeprecated shouldBe false
    }

    "parse service-type" in {
      val manifest =
        """
          |type: service
          |service-type: frontend
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined       shouldBe true
      details.get.repoType    shouldBe Some(RepoType.Service)
      details.get.serviceType shouldBe Some(ServiceType.Frontend)
    }

    "parse invalid service-type" in {
      val manifest =
        """
          |type: service
          |service-type: bad
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined       shouldBe true
      details.get.repoType    shouldBe Some(RepoType.Service)
      details.get.serviceType shouldBe None
    }

    "parse tags" in {
      val manifest =
        """
          |type: service
          |service-type: frontend
          |tags: ['api', 'stub', 'admin']
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined       shouldBe true
      details.get.repoType    shouldBe Some(RepoType.Service)
      details.get.serviceType shouldBe Some(ServiceType.Frontend)
      details.get.tags        shouldBe Some(Set(Tag.Api, Tag.Stub, Tag.AdminFrontend))
    }

    "parse invalid tag" in {
      val manifest =
        """
          |type: service
          |service-type: frontend
          |tags: ['bad']
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined       shouldBe true
      details.get.repoType    shouldBe Some(RepoType.Service)
      details.get.serviceType shouldBe Some(ServiceType.Frontend)
      details.get.tags        shouldBe Some(Set.empty)
    }

    "parse prototype-auto-publish" in {
      val manifest =
        """
          |prototype-name: my-prototype
          |prototype-auto-publish: true
          |""".stripMargin

      val details = ManifestDetails.parse("my-prototype", manifest)
      details.isDefined                shouldBe true
      details.get.prototypeName        shouldBe Some("my-prototype")
      details.get.prototypeAutoPublish shouldBe Some(true)
    }
  }
}
