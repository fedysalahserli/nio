package controllers

import auth.{AuthAction, SecuredAction, SecuredAuthContext}
import configuration.Env
import controllers.ErrorManager._
import db._
import db.AccountDataStore
import messaging.KafkaMessageBroker
import models.{Tenant, TenantCreated, TenantDeleted, Tenants}
import play.api.mvc.{ActionBuilder, AnyContent, ControllerComponents}
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
class TenantController(
    AuthAction: ActionBuilder[SecuredAuthContext, AnyContent],
    tenantStore: TenantDataStore,
    accountDataStore: AccountDataStore,
    lastConsentFactDataStore: LastConsentFactDataStore,
    consentFactStore: ConsentFactDataStore,
    userExtractTaskDataStore: UserExtractTaskDataStore,
    organisationDataStore: OrganisationDataStore,
    userDataStore: UserDataStore,
    extractionTaskDataStore: ExtractionTaskDataStore,
    deletionTaskDataStore: DeletionTaskDataStore,
    conf: Configuration,
    cc: ControllerComponents,
    env: Env,
    broker: KafkaMessageBroker)(implicit ec: ExecutionContext)
    extends ControllerUtils(cc) {

  implicit val readable: ReadableEntity[Tenant] = Tenant
  def tenants = AuthAction.async { implicit req =>
    tenantStore.findAll().map { tenants =>
      renderMethod(Tenants(tenants))
    }
  }

  def createTenant = AuthAction(bodyParser).async { implicit req =>
    req.headers.get(env.tenantConfig.admin.header) match {
      case Some(secret) if secret == env.tenantConfig.admin.secret =>
        req.body.read[Tenant] match {
          case Left(error) =>
            Logger.error("Invalid tenant format " + error)
            Future.successful("error.invalid.tenant.format".badRequest())
          case Right(tenant) =>
            tenantStore.findByKey(tenant.key).flatMap {
              case Some(_) =>
                Future.successful("error.key.already.used".conflict())
              case None =>
                tenantStore.insert(tenant).flatMap { _ =>
                  broker.publish(
                    TenantCreated(tenant = tenant.key,
                                  payload = tenant,
                                  metadata = req.authInfo.metadatas))

                  Future
                    .sequence(
                      Seq(
                        accountDataStore
                          .init(tenant.key)
                          .map(
                            _ => accountDataStore.ensureIndices(tenant.key)
                          ),
                        consentFactStore
                          .init(tenant.key)
                          .map(
                            _ => consentFactStore.ensureIndices(tenant.key)
                          ),
                        lastConsentFactDataStore
                          .init(tenant.key)
                          .map(
                            _ =>
                              lastConsentFactDataStore.ensureIndices(tenant.key)
                          ),
                        organisationDataStore
                          .init(tenant.key)
                          .map(
                            _ => organisationDataStore.ensureIndices(tenant.key)
                          ),
                        userDataStore
                          .init(tenant.key)
                          .map(
                            _ => userDataStore.ensureIndices(tenant.key)
                          ),
                        extractionTaskDataStore
                          .init(tenant.key)
                          .map(
                            _ =>
                              extractionTaskDataStore.ensureIndices(tenant.key)
                          ),
                        deletionTaskDataStore
                          .init(tenant.key)
                          .map(
                            _ => deletionTaskDataStore.ensureIndices(tenant.key)
                          ),
                        userExtractTaskDataStore
                          .init(tenant.key)
                          .map(
                            _ => deletionTaskDataStore.ensureIndices(tenant.key)
                          )
                      )
                    )
                    .map(_ => renderMethod(tenant, Created))
                }
            }
        }
      case _ =>
        Future.successful("error.missing.secret".unauthorized())
    }
  }

  def deleteTenant(tenantKey: String) = AuthAction.async { implicit req =>
    req.headers.get(env.tenantConfig.admin.header) match {
      case Some(secret) if secret == env.tenantConfig.admin.secret =>
        tenantStore.findByKey(tenantKey).flatMap {
          case Some(tenantToDelete) =>
            import cats.implicits._
            (
              consentFactStore.deleteConsentFactByTenant(tenantKey),
              lastConsentFactDataStore.deleteConsentFactByTenant(tenantKey),
              organisationDataStore.deleteOrganisationByTenant(tenantKey),
              userDataStore.deleteUserByTenant(tenantKey),
              tenantStore.removeByKey(tenantKey),
              accountDataStore.deleteAccountByTenant(tenantKey),
              deletionTaskDataStore.deleteDeletionTaskByTenant(tenantKey),
              extractionTaskDataStore.deleteExtractionTaskByTenant(tenantKey),
              userExtractTaskDataStore.deleteUserExtractTaskByTenant(tenantKey)
            ).mapN { (_, _, _, _, _, _, _, _, _) =>
              broker.publish(
                TenantDeleted(tenant = tenantToDelete.key,
                              payload = tenantToDelete,
                              metadata = req.authInfo.metadatas))
              Ok
            }
          case None =>
            Future.successful("error.tenant.not.found".notFound())
        }
      case _ => Future.successful("error.missing.secret".unauthorized())
    }
  }
}
