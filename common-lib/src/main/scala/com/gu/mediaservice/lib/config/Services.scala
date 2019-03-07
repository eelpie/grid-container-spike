package com.gu.mediaservice.lib.config

class Services(val config: CommonConfig) {

  private val baseUrl = "http://10.0.45.11"

  val domainRoot = "10.0.45.11" // TODO this is been used a a login domain by auth

  val kahunaBaseUri      = baseUrl + ":32105"
  val apiBaseUri         = baseUrl + ":32101"
  val loaderBaseUri      = baseUrl + ":32103"
  val cropperBaseUri     = baseUrl + ":32106"
  val metadataBaseUri    = baseUrl + ":32107"
  val imgopsBaseUri      = baseUrl + ":32108"
  val usageBaseUri       = baseUrl + ":80"  // TODO in use
  val collectionsBaseUri = baseUrl + ":32110"
  val leasesBaseUri      = baseUrl + ":32112"
  val authBaseUri        = baseUrl + ":32111"


  val apiHost = "10.0.45.11"

  val guardianWitnessBaseUri: String = "https://n0ticeapis.com"

  val toolsDomains: Set[String] = Set()
  // TODO move to config
  val corsAllowedTools: Set[String] = toolsDomains.foldLeft(Set[String]()) {(acc, domain) => {
    acc
  }}

  val loginUriTemplate = s"$authBaseUri/login{?redirectUri}"

}
