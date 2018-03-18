package com.q4.backoffice.tools.restapi.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.q4.backoffice.tools.restapi.message.GetIdentity;
import com.q4.backoffice.tools.restapi.message.GetQuery;
import com.q4.backoffice.tools.restapi.message.LogRequest;
import com.q4.backoffice.tools.restapi.message.LogResponse;

public class RequestSupervisor extends AbstractActor {

	private ActorRef requestResponseLogger;
	private ActorRef identityRequestActor;
	private ActorRef queryRequestActor;

	public RequestSupervisor() {
		requestResponseLogger = getContext().actorOf(RequestResponseLogger.props(), "RequestResponseLogger");
		identityRequestActor = getContext().actorOf(IdentityActor.props(), "IdentityActor");
		queryRequestActor = getContext().actorOf(QueryActor.props(), "QueryActor");
	}

	public static Props props() {
		return Props.create(RequestSupervisor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(LogRequest.class, 	this::logHttpRequest)
				.match(LogResponse.class, 	this::logHttpResponse)
				.match(GetIdentity.class, 	this::processIdentityRequest)
				.match(GetQuery.class, 		this::processQueryRequest)
				.build();
	}

	private void logHttpRequest(LogRequest logRequest) {
		requestResponseLogger.tell(logRequest, getSelf());
	}

	private void logHttpResponse(LogResponse logResponse) {
		requestResponseLogger.tell(logResponse, getSelf());
	}

	private void processIdentityRequest(GetIdentity request) {
		identityRequestActor.forward(request, getContext());
	}

	private void processQueryRequest(GetQuery request) {
		queryRequestActor.forward(request, getContext());
	}

}
