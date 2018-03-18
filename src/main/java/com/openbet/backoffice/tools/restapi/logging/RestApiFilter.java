package com.openbet.backoffice.tools.restapi.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class RestApiFilter extends Filter<ILoggingEvent> {

	@Override
	public FilterReply decide(ILoggingEvent event) {
		if (event.getFormattedMessage().contains("ID: ")) {
			return FilterReply.ACCEPT;
		}
		else {
			return FilterReply.DENY;
		}
	}
}
