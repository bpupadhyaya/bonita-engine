/**
 * Copyright (C) 2015 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation
 * version 2.1 of the License.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth
 * Floor, Boston, MA 02110-1301, USA.
 **/
package org.bonitasoft.engine;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.naming.Context;

import org.bonitasoft.engine.exception.BonitaException;
import org.bonitasoft.engine.exception.BonitaHomeNotSetException;
import org.bonitasoft.engine.home.BonitaHomeServer;
import org.bonitasoft.engine.test.APITestUtil;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestsInitializer {

    private static final String BONITA_HOME_DEFAULT_PATH = "target/bonita-home";

    private static final String BONITA_HOME_PROPERTY = "bonita.home";

    private static TestsInitializer INSTANCE;
    private Object h2Server;

    private static TestsInitializer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TestsInitializer();
        }
        return INSTANCE;
    }

    public static void beforeAll() throws Exception {
        TestsInitializer.getInstance().before();
    }

    public static void afterAll() throws Exception {
        TestsInitializer.getInstance().after();

    }

    protected void before() throws Exception {
        System.out.println("=====================================================");
        System.out.println("=========  INITIALIZATION OF TEST ENVIRONMENT =======");
        System.out.println("=====================================================");

        final long startTime = System.currentTimeMillis();
        setSystemPropertyIfNotSet(BONITA_HOME_PROPERTY, BONITA_HOME_DEFAULT_PATH);
        final String dbVendor = setSystemPropertyIfNotSet("sysprop.bonita.db.vendor", "h2");

        // Force these system properties
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.bonitasoft.engine.local.SimpleMemoryContextFactory");
        System.setProperty(Context.URL_PKG_PREFIXES, "org.bonitasoft.engine.local");

        if ("h2".equals(dbVendor)) {
            this.h2Server = startH2Server();
        }

        initPlatformAndTenant();

        System.out.println("==== Finished initialization (took " + (System.currentTimeMillis() - startTime) / 1000 + "s)  ===");
    }

    private Object startH2Server() throws ClassNotFoundException, NoSuchMethodException, IOException, BonitaHomeNotSetException, InvocationTargetException, IllegalAccessException {
        final String h2Port = (String) BonitaHomeServer.getInstance().getPrePlatformInitProperties().get("h2.db.server.port");
        final String[] args = new String[]{"-tcp", "-tcpAllowOthers", "-tcpPort", h2Port};

        final Class<?> h2ServerClass = Class.forName("org.h2.tools.Server");
        final Method createTcpServer = h2ServerClass.getMethod("createTcpServer", new Class[] {String[].class});
        final Object server = createTcpServer.invoke(createTcpServer, new Object[]{args});
        final Method start = server.getClass().getMethod("start");
        start.invoke(server);
        System.err.println("--- H2 Server started on port " + h2Port + " ---");
        return server;
    }

    private void stopH2Server(Object h2Server) throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException {
        final Class<?> h2ServerClass = Class.forName("org.h2.tools.Server");
        final Method stop = h2ServerClass.getMethod("stop");
        stop.invoke(h2Server);
        System.err.println("--- H2 Server stopped ---");
    }

    protected void after() throws Exception {
        System.out.println("=====================================================");
        System.out.println("============ CLEANING OF TEST ENVIRONMENT ===========");
        System.out.println("=====================================================");

        try {
            deleteTenantAndPlatform();
            checkThreadsAreStopped();
        } finally {
            if (this.h2Server != null) {
                stopH2Server(this.h2Server);
            }
        }
    }

    protected void deleteTenantAndPlatform() throws BonitaException {
        final APITestUtil apiTestUtil = new APITestUtil();
        apiTestUtil.stopAndCleanPlatformAndTenant(true);
        apiTestUtil.deletePlatformStructure();
    }

    private void checkThreadsAreStopped() throws InterruptedException {
        System.out.println("Checking if all Threads are stopped");
        final Set<Thread> keySet = Thread.getAllStackTraces().keySet();
        final Iterator<Thread> iterator = keySet.iterator();
        final ArrayList<Thread> list = new ArrayList<Thread>();
        while (iterator.hasNext()) {
            final Thread thread = iterator.next();
            if (isEngine(thread) && !thread.getName().startsWith("net.sf.ehcache.CacheManager")) {
                // wait for the thread to die
                thread.join(10000);
                // if still alive print it
                if (thread.isAlive()) {
                    list.add(thread);
                }
            }
        }
        if (!list.isEmpty()) {
            for (final Thread thread : list) {
                System.out.println("thread is still alive:" + thread.getName());
                System.err.println(thread.getStackTrace());
            }
            throw new IllegalStateException("Some threads are still active : " + list);
        }
        System.out.println("All engine threads are stopped properly");
    }

    private boolean isEngine(final Thread thread) {
        final String name = thread.getName();
        final ThreadGroup threadGroup = thread.getThreadGroup();
        if (threadGroup != null && threadGroup.getName().equals("system")) {
            return false;
        }
        final List<String> startWithFilter = Arrays.asList("H2 ", "Timer-0" /* postgres driver related */, "BoneCP", "bitronix", "main", "Reference Handler",
                "Signal Dispatcher", "Finalizer", "com.google.common.base.internal.Finalizer"/* guava, used by bonecp */, "process reaper", "ReaderThread",
                "Abandoned connection cleanup thread", "AWT-AppKit"/* bonecp related */, "Monitor Ctrl-Break"/* Intellij */);
        for (final String prefix : startWithFilter) {
            if (name.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    protected void initPlatformAndTenant() throws Exception {
        new APITestUtil().createPlatformStructure();
        new APITestUtil().initializeAndStartPlatformWithDefaultTenant(true);
    }

    private static String setSystemPropertyIfNotSet(final String property, final String value) {
        final String finalValue = System.getProperty(property, value);
        System.setProperty(property, finalValue);
        return finalValue;
    }
}
