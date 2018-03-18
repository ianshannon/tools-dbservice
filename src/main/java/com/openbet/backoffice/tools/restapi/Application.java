package com.openbet.backoffice.tools.restapi;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.openbet.backoffice.tools.restapi.config.AppConfig;
import com.openbet.backoffice.tools.restapi.config.AppConstants;
import com.openbet.backoffice.tools.restapi.http.HttpServer;
import com.openbet.backoffice.tools.restapi.permissions.Permissions;

import java.util.concurrent.CompletionStage;

public class Application {

	ActorSystem actorSystem = null;
	Permissions permissions;
	private LoggingAdapter log;
	private HttpServer httpServer;

	public static void main(String[] args) throws Exception {
		final Application app = new Application();
		app.initialise();
		app.startHttpServer();
	}

	public void initialise() throws Exception {
		actorSystem = ActorSystem.create("identity-routes");
		log = Logging.getLogger(actorSystem, this);
		httpServer = new HttpServer(actorSystem);
		permissions = Permissions.getInstance(false, true);		//initialise CoreSecurity - get permission messages flowing
	}

	private void startHttpServer() throws Exception {

		String host;
		int port;

		String cmdLineHost = System.getProperty(AppConstants.CONF_CMDLINE_HOST, "");
		if (!cmdLineHost.isEmpty() && (cmdLineHost.indexOf(":") > 0)) {

			int p = cmdLineHost.indexOf(":");
			host = cmdLineHost.substring(0,p);

			port = Integer.parseInt(cmdLineHost.substring(p + 1));	//will Throw exception if not-numeric

			log.debug("HOST from commandline {}({},{})",cmdLineHost,host,port);

		} else {

			host = AppConfig.getString(AppConstants.APP_NAME + ".http.host");
			if (host.trim().isEmpty()) {
				throw new Exception("Host not set in config");
			}
			port = AppConfig.getInt(AppConstants.APP_NAME + ".http.port");
			if (port <= 0) {
				throw new Exception("Invalid Port set in config");
			}
		}

		final ConnectHttp connectHttpHost = ConnectHttp.toHost(host, port);

		final Http http = Http.get(actorSystem);

		final ActorMaterializer materializer = ActorMaterializer.create(actorSystem);

		final Route routes = httpServer.createRoutes();
		final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = routes.flow(actorSystem, materializer);

		final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow, connectHttpHost, materializer);

		String msg  = String.format("SERVICE ONLINE at http://%s:%s/%s/%s/\t(%s cores) JVM %smb (free %smb, max %smb)",host,port,AppConstants.APP_NAME,AppConstants.APP_VERSION,
				Runtime.getRuntime().availableProcessors(),
				Runtime.getRuntime().totalMemory() / 1048576,
				Runtime.getRuntime().freeMemory() / 1048576,
				Runtime.getRuntime().maxMemory() / 1048576);
		log.info(msg);
		System.out.println("\n\n" + msg + "\n\n");
	}

	public HttpServer getHttpServer() { return this.httpServer; }
}
