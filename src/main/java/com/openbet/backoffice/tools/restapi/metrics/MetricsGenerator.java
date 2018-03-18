package com.openbet.backoffice.tools.restapi.metrics;

import akka.http.javadsl.model.StatusCode;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.GarbageCollectorExports;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import static io.prometheus.client.CollectorRegistry.defaultRegistry;

public class MetricsGenerator {

	private static Counter request_counter = Counter.build()
			.name("http_requests_total")
			.help("Total http requests for each method type.")
			.labelNames("method").register();

	private static Counter response_status_count = Counter.build()
			.name("response_status_count")
			.help("Total number of responses with status code and reason.")
			.labelNames("status_code", "reason").register();

	private static Gauge request_time_seconds = Gauge.build()
			.name("request_time_seconds")
			.help("Time taken for last request to the service in seconds. Does not include time taken to authorise.")
			.labelNames("method").register();

	private static Gauge query_time_seconds = Gauge.build()
			.name("query_time_seconds")
			.help("Time taken for last request to query postgres DB in seconds.")
			.labelNames("method").register();

	private static GarbageCollectorExports jvmGarbageMetricCollector = new GarbageCollectorExports();

	public static void registerJVMMetrics() {
		jvmGarbageMetricCollector.register();
	}

	public static void unregisterJVMMetrics() {
		defaultRegistry.unregister(jvmGarbageMetricCollector);
	}

	public static void increaseRequestCounter(String methodType) {
		request_counter.labels(methodType).inc();
	}

	public static void increaseResponseCounter(StatusCode statusCode) {
		response_status_count.labels(String.valueOf(statusCode.intValue()), statusCode.reason()).inc();
	}

	public static Gauge.Timer startRequestTimer(String methodType){
		return request_time_seconds.labels(methodType).startTimer();
	}

	public static Gauge.Timer startQueryTimer(String methodType){
		return query_time_seconds.labels(methodType).startTimer();
	}

	public static String getMetricData() throws IOException {
		Writer writer = new StringWriter();
		TextFormat.write004(writer, defaultRegistry.metricFamilySamples());

		return writer.toString();
	}

}
