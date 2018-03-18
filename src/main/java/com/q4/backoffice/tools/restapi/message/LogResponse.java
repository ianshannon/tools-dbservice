package com.q4.backoffice.tools.restapi.message;

import akka.http.javadsl.model.StatusCode;

public class LogResponse {
	private StatusCode statusCode;
	private String requestId;
	private String response;
//	ErrorCodes errorCode;

	public LogResponse(StatusCode statusCode, String requestId, String response) {
		this.statusCode = statusCode;
		this.requestId = requestId;
		this.response = response;
//		this.errorCode = errorCode;
	}

	public StatusCode getStatusCode() { return statusCode; }
	public String getRequestId() { return requestId; }
	public String getResponse() { return response; }
//	public ErrorCodes getErrorCode() { return errorCode; }
}
