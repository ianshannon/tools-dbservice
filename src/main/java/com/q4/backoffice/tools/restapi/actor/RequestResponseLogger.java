package com.q4.backoffice.tools.restapi.actor;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import com.q4.backoffice.tools.restapi.message.LogRequest;
import com.q4.backoffice.tools.restapi.message.LogResponse;

public class RequestResponseLogger extends AbstractActor {

	private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

	static Props props() {
		return Props.create(RequestResponseLogger.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(LogRequest.class, 	this::logHttpRequest)
				.match(LogResponse.class, 	this::logHttpResponse)
				.build();
	}

	private void logHttpRequest(LogRequest logRequest) {
		HttpRequest request = logRequest.getRequest();
		String requestBody = logRequest.getRequestBody();
		String userId = logRequest.getUserId();

		StringBuilder reqMsg = new StringBuilder();

		reqMsg.append("ID: ").append(logRequest.getRequestId()).append(" ");

		if (request != null) {
			reqMsg.append(request.method().value()).append(" '")
				.append(request.getUri().path()).append("' ")
				.append("User: '").append(userId).append("' ");
			if (request.method().value().toUpperCase().equals("POST")) {
				reqMsg.append("' Body: '").append(requestBody).append("'");
			}
		}
		else {
			reqMsg.append(" : Request details not available'");
		}

		log.info(reqMsg.toString());

	}

	private void logHttpResponse(LogResponse logResponse) {
		StatusCode sc = logResponse.getStatusCode();

		StringBuilder respMsg = new StringBuilder();

		respMsg.append("ID: ").append(logResponse.getRequestId()).append(" ");
		respMsg.append("StatusCode: ").append(sc.toString()).append(" ");

		if (logResponse.getStatusCode() == StatusCodes.OK) {
			respMsg.append("Response: '")
					.append(logResponse.getResponse().replace("\r\n", " "))
					.append("' ");
		}
		else {
			respMsg.append("Response: '")
					.append(logResponse.getStatusCode().reason()).append(" ")
					.append(logResponse.getResponse().replace("\r\n", " "))
					.append("' ");
		}
		log.info(respMsg.toString());
	}
}
