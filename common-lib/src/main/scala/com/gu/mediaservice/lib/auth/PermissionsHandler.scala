package com.gu.mediaservice.lib.auth

import com.gu.mediaservice.lib.auth.Authentication.{AuthenticatedService, PandaUser, Principal}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.permissions.{PermissionDefinition, PermissionsConfig, PermissionsProvider}

trait PermissionsHandler {
  def storeIsEmpty: Boolean
  def hasPermission(user: Principal, permission: PermissionDefinition): Boolean
}

class GrantAllPermissionsHandler extends PermissionsHandler {
  override def storeIsEmpty: Boolean = false
  override def hasPermission(user: Principal, permission: PermissionDefinition): Boolean = true
}

class GuardianEditorialPermissionsHandler(bucket: String, stage: String, config: CommonConfig) extends PermissionsHandler {

  private val permissionsConfig = PermissionsConfig(
    stage = stage,
    region = config.awsRegion,
    awsCredentials = config.awsCredentials,
    s3Bucket = bucket
  )

  private val permissions = PermissionsProvider(permissionsConfig)

  override def storeIsEmpty: Boolean = {
    permissions.storeIsEmpty
  }

  override def hasPermission(user: Principal, permission: PermissionDefinition): Boolean = {
    user match {
      case PandaUser(u) => permissions.hasPermission(permission, u.email)
      // think about only allowing certain services i.e. on `service.name`?
      case AuthenticatedService(_) => true
      case _ => false
    }
  }

}