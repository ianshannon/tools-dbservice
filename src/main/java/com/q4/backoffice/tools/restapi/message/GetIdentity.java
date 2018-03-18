package com.q4.backoffice.tools.restapi.message;

public class GetIdentity {
	private String reqId;
	private String userid;
	private String identityUuid;

	public GetIdentity(String reqId, String userid, String identityUuid) {
		this.reqId = reqId;
		this.userid = userid;
		this.identityUuid = identityUuid;
	}

	public String getReqId() {
		return reqId;
	}

	public String getIdentityUuid() {
		return identityUuid;
	}

	public String getUserid() { return userid; }
}
