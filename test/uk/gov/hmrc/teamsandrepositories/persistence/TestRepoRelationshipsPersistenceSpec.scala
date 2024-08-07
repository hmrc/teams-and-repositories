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

package uk.gov.hmrc.teamsandrepositories.persistence

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.teamsandrepositories.persistence.TestRepoRelationshipsPersistence.TestRepoRelationship

import scala.concurrent.ExecutionContext.Implicits.global

class TestRepoRelationshipsPersistenceSpec
  extends AnyWordSpec
     with Matchers
     with DefaultPlayMongoRepositorySupport[TestRepoRelationship]:

  override protected val repository: TestRepoRelationshipsPersistence = TestRepoRelationshipsPersistence(mongoComponent)

  "putRelationships" should:
    "replace all relationships for a given service" in:
      insert(TestRepoRelationship("test-repo-1", "service-repo-1")).futureValue
      insert(TestRepoRelationship("test-repo-2", "service-repo-2")).futureValue

      repository.findTestReposByService("service-repo-1").futureValue shouldBe Seq("test-repo-1")
      repository.findTestReposByService("service-repo-2").futureValue shouldBe Seq("test-repo-2")

      repository
        .putRelationships(
          "service-repo-1",
          Seq(
            TestRepoRelationship("service-performance-tests", "service-repo-1"),
            TestRepoRelationship("service-acceptance-tests", "service-repo-1")
          )
        ).futureValue

      repository.findTestReposByService("service-repo-1").futureValue should contain theSameElementsAs Seq("service-performance-tests", "service-acceptance-tests")
      repository.findTestReposByService("service-repo-2").futureValue should contain theSameElementsAs Seq("test-repo-2")

  "findTestReposByService" should:
    "return all test repos associated with a given service" in:
      insert(TestRepoRelationship("test-repo-one", "service-repo")).futureValue
      insert(TestRepoRelationship("test-repo-two", "service-repo")).futureValue

      repository.findTestReposByService("service-repo").futureValue should contain theSameElementsAs Seq("test-repo-one", "test-repo-two")

  "findServicesByTestRepo" should:
    "return all services that are associated with a given test repo" in:
      insert(TestRepoRelationship("test-repo", "service-one")).futureValue
      insert(TestRepoRelationship("test-repo", "service-two")).futureValue

      repository.findServicesByTestRepo("test-repo").futureValue should contain theSameElementsAs Seq("service-one", "service-two")
