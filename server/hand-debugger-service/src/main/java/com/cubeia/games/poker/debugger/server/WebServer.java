package com.cubeia.games.poker.debugger.server;

import java.net.URL;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

import com.cubeia.games.poker.debugger.guice.GuiceConfig;
import com.cubeia.games.poker.debugger.web.EmptyServlet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceFilter;

@Singleton
public class WebServer implements Runnable {

	@Inject GuiceConfig guice;
	private Server server;

	Thread thread;

	public void start() {
		thread = new Thread(this);
		thread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
		thread.setDaemon(true);
		thread.start();
	}

	public void run() {
		try {
			server = new Server(9091);

			// Static resources
			URL url = getClass().getResource("/html/base_index_file.html");
			String resource = url.toString().replaceAll("base_index_file.html", "");
			
			ResourceHandler resource_handler = new ResourceHandler();
			resource_handler.setDirectoriesListed(true);
			resource_handler.setResourceBase(resource);

			// Dynamic content
			ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
			context.setClassLoader(getClass().getClassLoader());
			context.setContextPath("/api");
			context.addEventListener(guice);
			
			FilterHolder filterHolder = new FilterHolder(GuiceFilter.class);
			context.addFilter(filterHolder, "/*", EnumSet.allOf(DispatcherType.class));
			context.addServlet(EmptyServlet.class, "/*");

			// Add all handlers to server
			HandlerList handlers = new HandlerList();
			handlers.setHandlers(new Handler[] { resource_handler, context, new DefaultHandler() });
			server.setHandler(handlers);

			server.start();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void stop() {
		try {
			server.stop();
		} catch (Exception e) {}
	}
}