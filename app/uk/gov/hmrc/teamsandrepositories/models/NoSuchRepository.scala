package uk.gov.hmrc.teamsandrepositories.models

final case class NoSuchRepository(repoName: String) extends Throwable {

  val message: String =
    s"No such repository '$repoName' exists"
}
