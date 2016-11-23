package uk.gov.hmrc.teamsandrepositories

import uk.gov.hmrc.teamsandrepositories.DataGetter.DataLoaderFunction

import scala.concurrent.Future

trait DataGetter[T] {
  val runner: DataLoaderFunction[T]
}

case class FileDataGetter(runner: DataLoaderFunction[TeamRepositories]) extends DataGetter[TeamRepositories]
case class GithubDataGetter(runner: DataLoaderFunction[TeamRepositories]) extends DataGetter[TeamRepositories]

object DataGetter {
  type DataLoaderFunction[T] = () => Future[Seq[T]]
}

