package uk.gov.hmrc.catalogue.github

import java.io.File

trait GithubCredentials extends CredentialsFinder {
  def cred: ServiceCredentials
}

trait StoredCredentials extends GithubCredentials {
  val cred: ServiceCredentials = new File(System.getProperty("user.home"), ".github").listFiles()
    .flatMap { c => findGithubCredsInFile(c.toPath) }.head
}
