package com.openbet.backoffice.tools.restapi.http;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.pattern.PatternsCS;
import com.openbet.backoffice.tools.restapi.config.AppConfig;
import com.openbet.backoffice.tools.restapi.config.AppConstants;
import com.openbet.backoffice.tools.restapi.db.DbQuery;
import com.openbet.backoffice.tools.restapi.message.GetIdentity;
import com.openbet.backoffice.tools.restapi.message.GetQuery;
import com.openbet.backoffice.tools.restapi.message.LogRequest;
import com.openbet.backoffice.tools.restapi.message.LogResponse;
import com.openbet.backoffice.tools.restapi.actor.RequestSupervisor;
import com.openbet.backoffice.tools.restapi.metrics.MetricsGenerator;
import com.openbet.backoffice.tools.restapi.utilities.ResourceFile;
import io.prometheus.client.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.function.BiFunction;

import static akka.http.javadsl.model.ContentTypes.*;

/**
 * Identity service - one top-level (/identity/v1.0/identities) endpoint (under /product/version)
 * 		second level endpoint for single identity
 *
 * 	return list of identities - containing just userName
 *		?fields=identities/username
 *
 *
 *
 */

public class HttpServer extends AllDirectives {

	private static final Logger LOG = LoggerFactory.getLogger(HttpServer.class);
	private static String module = "TL-HTTP\t";

	private LoggingAdapter log;
	private ActorRef requestSupervisor;

//	private Queries queries;

	private String defaultUser = "guest";

	private long requestTimeoutInMS;
	private int minRequestId;
	private int maxRequestId;

	private String securityMessage_UserId;
	private String securityMessage_NotAllowed;
	private String securityMessage_Invalid;

	private String securityMessage_NoReport;
	private String securityMessage_NeedUserId;

	private ResourceFile resourceFile = new ResourceFile();

	/**
	 * Constructor
	 *
	 * @param actorSystem		Actor System to use
	 */
	public HttpServer(ActorSystem actorSystem) {

		log = Logging.getLogger(actorSystem, this);

		requestSupervisor = actorSystem.actorOf(RequestSupervisor.props(), "RequestSupervisor");

		requestTimeoutInMS = AppConfig.getInt(AppConstants.APP_NAME + ".timeoutInSeconds.request") * 1000;
		minRequestId = AppConfig.getInt(AppConstants.APP_NAME + ".defaults.minRequestId", 1000);
		maxRequestId = AppConfig.getInt(AppConstants.APP_NAME + ".defaults.maxRequestId");

		try {        //TODO - this needs changing - either.unregister around HttpServer - or use with 'around' application
			MetricsGenerator.registerJVMMetrics();
		} catch (Exception ex) {
			log.error("Metrics exception:" + ex.getMessage());
		}

		String serviceName = AppConfig.getString("core-security.serviceName", AppConstants.APP_NAME);

		securityMessage_UserId = AppConfig.getString(serviceName + ".message_UserId", "");
		securityMessage_NotAllowed = AppConfig.getString(serviceName + ".message_NotAllowed", "");
		securityMessage_Invalid = AppConfig.getString(serviceName + ".message_Invalid", "");

		securityMessage_NoReport = AppConfig.getString(serviceName + ".message_NoReport", "");
		securityMessage_NeedUserId = AppConfig.getString(serviceName + ".message_NeedUserId", "");

//		String queriesDirectory = AppConfig.getString(serviceName + ".queriesDirectory","");
//		String driverName = AppConfig.getString(serviceName + ".dataSource.className","");
//		String databaseUrl = AppConfig.getString(serviceName + ".dataSource.url","");
//		queries = new Queries(queriesDirectory, driverName, databaseUrl);

	}
	/**
	 * Base route for application
	 *
	 * Extract userid - from header, param or default
	 *
	 * @return	Route created
	 */
	public Route createRoutes() {
		return extractUserAndRequest((userid, httpRequest) -> route(
					pathEndOrSingleSlash(() -> 							get(this::getApiDocumentation)),
//					pathPrefix("identities", () -> route (
//						pathPrefix("username", () -> 		pathEndOrSingleSlash(() -> get(() -> getIdentityRoute(userid, httpRequest, "")))),
//						pathPrefix((id) -> 							pathEndOrSingleSlash(() -> get(() -> getIdentityRoute(userid, httpRequest, id)))),
//																	pathEndOrSingleSlash(() -> get(() -> getIdentityRoute(userid, httpRequest, "")))
//					)
//					),
					extractUnmatchedPath(remainingUrl -> getQueryRoute(userid, httpRequest, ((remainingUrl==null) || (remainingUrl.length()<1))  ? "" : remainingUrl.toLowerCase().substring(1)))	//strip off leading '/' from 'remaining'
//					invalidRoute(userid, httpRequest)
			));
	}

	private Route getApiDocumentation(){

		try {
			return complete(HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, resourceFile.Load("asciidoc/html/api.html")));
		} catch (Exception ex) {
			LOG.debug("Error retrieving api documentation. " + ex.toString());
			return complete(HttpResponse.create().withEntity("API documentations could not be retrieved.").withStatus(503));
		}
	}
	private Route unauthorizedRoute() {
		return complete(StatusCodes.UNAUTHORIZED, securityMessage_NotAllowed);
	}

	private Route invalidRoute(String userid, HttpRequest httpRequest) {
		String message = securityMessage_Invalid.replace("{URL}",httpRequest.getUri().getPathString());
		LOG.info("INVALID-REQUEST\tUserId {}\t{}", userid, message);
		return complete(StatusCodes.BAD_REQUEST, message);
	}
	/**
	 * Wrap inner Route - supplying Userid and HttpRequest
	 *
	 * @param inner		Inner Route to run
	 * @return			result of running Inner Route
	 */
	private Route extractUserAndRequest(BiFunction<String, HttpRequest, Route> inner) {

		return extractRequest((httpRequest) -> //route (
			parameterOptional("userid", userid -> {
				if (!userid.isPresent()) {
					//TODO - add check for userid in Header
				}
				return pathPrefix(AppConstants.APP_NAME, () ->
						pathPrefix(AppConstants.APP_VERSION, () ->
								inner.apply(userid.orElse(defaultUser), httpRequest)
						)
				);
			})
		);
	}

//	private Route xqueryRoute(String userid, String reportName) {
//
//		if (userid.isEmpty()) {
//
//			LOG.info("REPORT-REQUEST\tNo User ID\t{} for {}", userid, reportName);
//			return complete(StatusCodes.UNAUTHORIZED, securityMessage_NeedUserId);
//
//		} else {
//
//			try {
//
//				//check for format in name
//				String contentTypeName = stringRight(reportName,".");
//				String name = stringLeft(reportName,".");
//				ContentType.NonBinary contentType = contentTypeFromName(contentTypeName);
//				DbQuery.ResponseFormat responseFormat = responseFormatFromName(contentTypeName);
//
//				String querySource = queries.queryFor(userid, name, responseFormat);
//				if (!querySource.isEmpty()) {
//					HttpResponse httpResponse = HttpResponse.create()
//							.withStatus(StatusCodes.OK)
//							.withEntity(HttpEntities.create(contentType, querySource))
//							.addHeader(RawHeader.create("Access-Control-Allow-Origin","*"))
//							;
//					return complete(httpResponse);
//					//					return complete(
//					//							StatusCodes.OK,
//					//							HttpEntities.create(ContentTypes.APPLICATION_JSON, querySource)
//					////							HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, querySource)
//					//					);
//				} else {
//					return complete(StatusCodes.BAD_REQUEST, securityMessage_NoReport);
//				}
//			} catch (AccessDeniedException e) {
//				return complete(StatusCodes.UNAUTHORIZED, securityMessage_NotAllowed);
//			}
//		}
//	}

	private Route getQueryRoute(String userid, HttpRequest request, String remainingUrl) {

		Route route;
		if (userid.isEmpty()) {

			LOG.info("INVALID-REQUEST\tNo User ID\t{}", userid);
			return complete(StatusCodes.UNAUTHORIZED, securityMessage_UserId);

		} else {

			Gauge.Timer requestTimer = MetricsGenerator.startRequestTimer(HttpMethods.GET.name());
			MetricsGenerator.increaseRequestCounter(HttpMethods.GET.name());
			String reqId = Integer.toString(generateRequestId());

			try {

				//Log the request
				requestSupervisor.tell(new LogRequest(reqId, request, null, userid), ActorRef.noSender());

				//check for format in name
				String contentTypeName = stringRight(remainingUrl,".");
				String name = stringLeft(remainingUrl,".");
				ContentType.NonBinary contentType = contentTypeFromName(contentTypeName);
				DbQuery.ResponseFormat responseFormat = responseFormatFromName(contentTypeName);

				GetQuery requestMessage = new GetQuery(reqId, userid, name, responseFormat);

				//Forward the request
				route = completeWithFuture(PatternsCS.ask(requestSupervisor, requestMessage, requestTimeoutInMS)
						.thenApply(response -> {
							HttpResponse httpResponse = composeAndLogResponse((LogResponse) response, contentType);
							requestTimer.setDuration();
							return httpResponse;
				}));
			} catch (Exception ex) {

				LogResponse logResponse = new LogResponse(StatusCodes.INTERNAL_SERVER_ERROR, reqId, "");
				route = complete(composeAndLogResponse(logResponse, ContentTypes.TEXT_PLAIN_UTF8));
				requestTimer.setDuration();
//				return route;
			}
		}
		return route;
	}

	private Route getIdentityRoute(String userid, HttpRequest request, String identityUuid) {

		Route getIdentityRoute;
		if (userid.isEmpty()) {

			LOG.info("INVALID-REQUEST\tNo User ID\t{}", userid);
			return complete(StatusCodes.UNAUTHORIZED, securityMessage_UserId);

		} else {

			Gauge.Timer requestTimer = MetricsGenerator.startRequestTimer(HttpMethods.GET.name());
			MetricsGenerator.increaseRequestCounter(HttpMethods.GET.name());
			String reqId = Integer.toString(generateRequestId());

			try {

				//Log the request
				requestSupervisor.tell(new LogRequest(reqId, request, null, userid), ActorRef.noSender());

				GetIdentity requestMessage = new GetIdentity(reqId, userid, identityUuid);

				//Forward the request
				getIdentityRoute = completeWithFuture(PatternsCS.ask(requestSupervisor, requestMessage, requestTimeoutInMS).thenApply(response -> {
					HttpResponse httpResponse = composeAndLogResponse((LogResponse) response, ContentTypes.APPLICATION_JSON);
					requestTimer.setDuration();
					return httpResponse;

				}));
			} catch (Exception ex) {

				LogResponse logResponse = new LogResponse(StatusCodes.INTERNAL_SERVER_ERROR, reqId, "");
				Route route = complete(composeAndLogResponse(logResponse, ContentTypes.TEXT_PLAIN_UTF8));
				requestTimer.setDuration();
				return route;
			}
		}
		return getIdentityRoute;
	}
	private int generateRequestId() {
		Random r = new Random();
		return r.nextInt((maxRequestId - minRequestId) + 1) + minRequestId;
	}
	private HttpResponse composeAndLogResponse(LogResponse logResponse, ContentType.NonBinary contentType) {

		HttpResponse httpResponse;

		if (logResponse.getStatusCode() == StatusCodes.OK) {
			httpResponse = HttpResponse.create()
					.withStatus(logResponse.getStatusCode())
					.withEntity(HttpEntities.create(contentType, logResponse.getResponse()))
					.addHeader(RawHeader.create("Access-Control-Allow-Origin","*"));
		} else {
			// Build error response
			String errorMessage = logResponse.getStatusCode().reason();
			httpResponse = HttpResponse.create()
					.withStatus(logResponse.getStatusCode())
					.withEntity(HttpEntities.create(TEXT_PLAIN_UTF8, errorMessage));
		}

		//Increase the response count for metric data
		MetricsGenerator.increaseResponseCounter(logResponse.getStatusCode());
		//log the response
		requestSupervisor.tell(logResponse, ActorRef.noSender());

		return httpResponse;
	}

	private DbQuery.ResponseFormat responseFormatFromName(String contentTypeName){
		switch (contentTypeName){
			case "json":
				return DbQuery.ResponseFormat.JSON;
			case "csv":
				return DbQuery.ResponseFormat.CSV;
			case "tsv":
				return DbQuery.ResponseFormat.TSV;
			default:
				return DbQuery.ResponseFormat.HTML;
		}
	}
	private ContentType.NonBinary contentTypeFromName(String contentTypeName){
		switch (contentTypeName){
			case "json":
				return ContentTypes.APPLICATION_JSON;
			case "csv":
			case "tsv":
				return ContentTypes.TEXT_CSV_UTF8;
			default:
				return ContentTypes.TEXT_HTML_UTF8;
		}
	}

	private String stringLeft(String source, String key){
		int posOfKey = source.indexOf(key);
		if (posOfKey>0) {
			return source.substring(0, posOfKey);
		}
		return source;
	}
	private String stringRight(String source, String key){
		int posOfKey = source.indexOf(key);
		if (posOfKey>0) {
			return source.substring(posOfKey + key.length());
		}
		return source;
	}
	private String stringMid(String source, String key, String endMarker) {
		int posOfKey = source.indexOf(key);
		if (posOfKey>0) {
			int posOfEndMarker = source.indexOf(endMarker);
			if (posOfEndMarker<0) {
				posOfEndMarker = source.length();
			}
			return source.substring(posOfKey + key.length(), posOfEndMarker);
		}
		return source;
	}

}
