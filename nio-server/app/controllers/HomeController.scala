package controllers

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import auth.AuthContextWithEmail
import configuration.Env
import controllers.ErrorManager.ErrorManagerResult
import db.TenantDataStore
import play.api.mvc.{ActionBuilder, AnyContent, ControllerComponents}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class HomeController(
    val AuthAction: ActionBuilder[AuthContextWithEmail, AnyContent],
    val cc: ControllerComponents,
    val tenantStore: TenantDataStore,
    val env: Env,
    val actorSystem: ActorSystem,
    implicit val ec: ExecutionContext)
    extends ControllerUtils(cc) {

  val accountManagement: Boolean = env.config.filter.securityMode match {
    case "default"  => true
    case "auth0"    => false
    case "otoroshi" => false
    case _          => true
  }

  val apiKeyManagement: Boolean = env.config.filter.securityMode match {
    case "default"  => true
    case "auth0"    => true
    case "otoroshi" => false
    case _          => true
  }

  private lazy val redirectSecurity: String = {
    env.config.filter.securityMode match {
      case "auth0" =>
        s"${env.config.baseUrl}/auth0/login"
      case "default" =>
        s"${env.config.baseUrl}/login"
      case _ =>
        s"${env.config.baseUrl}/login"
    }
  }

  lazy val swaggerContent: String = Files
    .readAllLines(env.environment.getFile("conf/swagger/swagger.json").toPath)
    .asScala
    .mkString("\n")
    .replace("$CLIENT_ID",
             env.config.filter.otoroshi.headerGatewayHeaderClientId)
    .replace("$CLIENT_SECRET",
             env.config.filter.otoroshi.headerGatewayHeaderClientSecret)

  def index(tenant: String) = AuthAction.async { implicit req =>
    req.authInfo match {
      case Some(authInfo) if authInfo.isAdmin =>
        tenantStore.findByKey(tenant).map {
          case Some(_) =>
            Ok(
              views.html
                .index(env,
                       tenant,
                       req.email,
                       accountManagement,
                       apiKeyManagement))
          case None => "error.tenant.not.found".notFound()
        }
      case Some(_) =>
        FastFuture.successful("error.forbidden.backoffice.access".forbidden())
      case None =>
        FastFuture.successful(Redirect(redirectSecurity))
    }
  }

  def indexNoTenant = AuthAction { implicit req =>
    req.authInfo match {
      case Some(authInfo) if authInfo.isAdmin =>
        Ok(
          views.html
            .indexNoTenant(env, req.email, accountManagement, apiKeyManagement))
      case Some(_) =>
        "error.forbidden.backoffice.access".forbidden()
      case None =>
        Redirect(redirectSecurity)
    }
  }

  def login = AuthAction { ctx =>
    Ok(views.html.indexLogin(env))
  }

  def indexOther(tenant: String) = index(tenant)

  def otherRoutes(tenant: String, route: String) = AuthAction.async {
    implicit req =>
      req.authInfo match {
        case Some(authInfo) if authInfo.isAdmin =>
          tenantStore.findByKey(tenant).map {
            case Some(_) =>
              Ok(
                views.html
                  .index(env,
                         tenant,
                         req.email,
                         accountManagement,
                         apiKeyManagement))
            case None => "error.tenant.not.found".notFound()
          }
        case Some(_) =>
          FastFuture.successful("error.forbidden.backoffice.access".forbidden())
        case None =>
          FastFuture.successful(Redirect(redirectSecurity))
      }
  }

  def swagger() = AuthAction { req =>
    Ok(swaggerContent).as("application/json")
  }
}
