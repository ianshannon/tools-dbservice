package com.openbet.backoffice.tools.restapi.message;

import com.openbet.backoffice.tools.restapi.db.DbQuery;

public class GetQuery {
	private String reqId;
	private String userid;
	private String remainingUrl;
	private DbQuery.ResponseFormat responseFormat;

	public GetQuery(String reqId, String userid, String remainingUrl, DbQuery.ResponseFormat responseFormat) {
		this.reqId = reqId;
		this.userid = userid;
		this.remainingUrl= remainingUrl;
		this.responseFormat = responseFormat;
	}

	public String getReqId() {
		return reqId;
	}

	public String getRemainingUrl() {return remainingUrl; }

	public DbQuery.ResponseFormat getResponseFormat() {return responseFormat; }

	public String getUserid() { return userid; }
}
