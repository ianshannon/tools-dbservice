package com.openbet.backoffice.tools.restapi.message;

import akka.http.javadsl.model.HttpRequest;

public class LogRequest {
	String requestId;
	HttpRequest request;
	String requestBody;
	String userId;

	public LogRequest(String requestId, HttpRequest request, String requestBody, String userId) {
		this.requestId = requestId;
		this.request = request;
		this.requestBody = requestBody;
		this.userId = userId;
	}

	public String getRequestId() { return requestId; }
	public HttpRequest getRequest(){ return request; }
	public String getRequestBody() { return requestBody; }
	public String getUserId() { return userId; }

}
