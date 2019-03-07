package com.gu.mediaservice.lib.config

class Services(val config: CommonConfig) {

  val baseHost = "grid.eelpieconsulting.co.uk"

  private val baseUrl = "https://" + baseHost
  val domainRoot = baseHost  // TODO this is been used a a login domain by auth

  val kahunaBaseUri      = baseUrl + "/"
  val apiBaseUri         = baseUrl + "/media-api"
  val loaderBaseUri      = baseUrl + "/image-loader"
  val cropperBaseUri     = baseUrl + "/cropper"
  val metadataBaseUri    = baseUrl + "/metadata-editor"
  val imgopsBaseUri      = baseUrl + "/imgops"
  val usageBaseUri       = baseUrl + "/usages"
  val collectionsBaseUri = baseUrl + "/collections"
  val leasesBaseUri      = baseUrl + "/leases"
  val authBaseUri        = baseUrl + "/auth"


  val apiHost = baseHost

  val guardianWitnessBaseUri: String = "https://n0ticeapis.com"

  val toolsDomains: Set[String] = Set()
  // TODO move to config
  val corsAllowedTools: Set[String] = toolsDomains.foldLeft(Set[String]()) {(acc, domain) => {
    acc
  }}

  val loginUriTemplate = s"$authBaseUri/login{?redirectUri}"

}
