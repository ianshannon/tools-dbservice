package com.openbet.backoffice.tools.restapi.actor;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.http.javadsl.model.StatusCodes;
import com.openbet.backoffice.tools.restapi.config.AppConfig;
import com.openbet.backoffice.tools.restapi.db.DbQuery;
import com.openbet.backoffice.tools.restapi.message.GetIdentity;
import com.openbet.backoffice.tools.restapi.message.LogResponse;
import com.openbet.backoffice.tools.restapi.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.openbet.backoffice.tools.restapi.config.AppConstants.*;
import static com.openbet.backoffice.tools.restapi.db.DbQuery.ResponseFormat.JSON;

public class IdentityActor extends AbstractActor {
	private static final Logger LOG = LoggerFactory.getLogger(IdentityActor.class);
	private static String module = "\tDB-IACT\t";

	private String query_SelectIdentities = "";
	private String query_SelectIdentity = "";

	private DbQuery dbQuery;
	private Permissions permissions;

	public IdentityActor() {

		//Load Queries from Config
		query_SelectIdentities = AppConfig.getString(APP_NAME + ".dataSource.query.selectIdentities");
		query_SelectIdentity = AppConfig.getString(APP_NAME + ".dataSource.query.selectIdentity");

		//Load db Driver and url from Config
		String driverName = AppConfig.getString(APP_NAME + ".dataSource.className");
		String databaseUrl = AppConfig.getString(APP_NAME + ".dataSource.url");
		dbQuery = new DbQuery(driverName, databaseUrl);

		permissions = Permissions.getInstance(false, true);
		//CoreSecuritySDK has now created KAFKA permissions Producer/Consumer for 'this service'
		//Note - will not yet have received permissions (from Kafka)
	}

	public static Props props(){
		return Props.create(IdentityActor.class);
	}

	@Override public Receive createReceive() {
		return receiveBuilder()
				.match(GetIdentity.class, this::processIdentityRequest)
//				.match(GetAllIdentity.class, this::processAllIdentityRequest)
				.build();
	}

	private void processIdentityRequest(GetIdentity r) {
		LogResponse logResponse;
		try {
			if (!permissions.checkedPermissionGranted(r.getUserid(), Permissions.PermissionItem.identities)) {
				logResponse = new LogResponse(StatusCodes.UNAUTHORIZED, r.getReqId(), "Not Authorised");
			} else {
				String result = dbQuery.Select(query_SelectIdentities, JSON, "identities", r.getIdentityUuid(), true);
				if (result.isEmpty()) {
					logResponse = new LogResponse(StatusCodes.NOT_FOUND, r.getReqId(), "Not Found");
				} else {
					logResponse = new LogResponse(StatusCodes.OK, r.getReqId(), result);
				}
			}
		} catch (Exception ex) {
			logResponse = new LogResponse(StatusCodes.INTERNAL_SERVER_ERROR, r.getReqId(), "Server Error");
			LOG.error("{}Exception 'processIdentityRequest' SQL: {}", module, ex.getMessage());
		}
		sender().tell(logResponse, getSelf());
	}
//	private void processAllIdentityRequest(GetAllIdentity r) {
//
//		LogResponse logResponse;
//		try {
//			if (!permissions.checkedPermissionGranted(r.getUserid(), Permissions.PermissionItem.allidentities)) {
//				logResponse = new LogResponse(StatusCodes.UNAUTHORIZED, r.getReqId(), "Not Authorised");
//			} else {
//				String result = dbQuery.Select(query_SelectIdentities, JSON, "identities", "", true);
//				if (result.isEmpty()) {
//					logResponse = new LogResponse(StatusCodes.NOT_FOUND, r.getReqId(), "Not Found");
//				} else {
//					logResponse = new LogResponse(StatusCodes.OK, r.getReqId(), result);
//				}
//			}
//		} catch (Exception ex) {
//			logResponse = new LogResponse(StatusCodes.INTERNAL_SERVER_ERROR, r.getReqId(), "Server Error");
//			LOG.error("{}Exception 'processAllIdentityRequest' : {}", module, ex.getMessage());
//		}
//		sender().tell(logResponse, getSelf());
//	}
}
