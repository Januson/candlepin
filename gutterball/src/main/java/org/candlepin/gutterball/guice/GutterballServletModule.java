/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.gutterball.guice;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.gutterball.curator.EventCurator;
import org.candlepin.gutterball.receive.EventReceiver;
import org.candlepin.gutterball.resource.EventResource;
import org.candlepin.gutterball.resource.StatusResource;
import org.candlepin.gutterball.resteasy.JsonProvider;
import org.candlepin.gutterball.servlet.GutterballServletContextListener;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Provides;
import com.google.inject.servlet.ServletModule;
import com.google.inject.servlet.ServletScopes;
import com.mongodb.DB;
import com.mongodb.MongoClient;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;
import javax.servlet.ServletContext;

/**
 * GutterballServletContextListener is responsible for starting Guice and binding
 * all the dependencies.
 */
public class GutterballServletModule extends ServletModule {
    protected void configureBindings() {
        // See JavaDoc on I18nProvider for more information of RequestScope
        bind(I18n.class).toProvider(I18nProvider.class).in(ServletScopes.REQUEST);
        bind(JsonProvider.class);
        bind(EventReceiver.class).asEagerSingleton();

        // It is safe to share a single instance of the mongodb connection
        bind(MongoClient.class).toProvider(MongoDBClientProvider.class).in(Singleton.class);
        // FIXME: Determine if we need to share the DB connection.
        bind(DB.class).toProvider(MongoDBProvider.class).in(Singleton.class);

        // Bind curators
        bind(EventCurator.class);

        // Bind resources
        bind(StatusResource.class);
        bind(EventResource.class);
    }

    /**
     * Guice does not normally allow providers to throw exceptions.  You can use the
     * ThrowingProviders extension (see http://code.google.com/p/google-guice/wiki/ThrowingProviders)
     * but this forces every user of the provider to catch the relevant exception.  If
     * we fail to load the configuration, we just want to abort application deployment, so
     * we read in the configuration in the ServletContextListener and throw any exceptions
     * there.  Afterwards, we place the configuration in the servlet context and grab it here.
     * @return
     */
    @Provides @Singleton
    protected Configuration provideConfiguration() {
        ServletContext context = getServletContext();
        return (Configuration) context.getAttribute(GutterballServletContextListener.CONFIGURATION_NAME);
    }

    @Override
    protected void configureServlets() {
        configureBindings();
        Map<String, String> loggingFilterConfig = new HashMap<String, String>();
        loggingFilterConfig.put("header.name", "x-gutterball-request-uuid");
        filter("/*").through(LoggingFilter.class, loggingFilterConfig);
    }
}