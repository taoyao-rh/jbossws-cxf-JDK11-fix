/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.test.ws.jaxws.cxf.gzip;

import java.net.URL;

import junit.framework.Test;

import org.jboss.ws.common.IOUtils;
import org.jboss.wsf.test.JBossWSCXFTestSetup;
import org.jboss.wsf.test.JBossWSTest;

/**
 * This covers the same tests of GZIPTestCase but runs them via a Servlet client
 * 
 * @author alessio.soldano@jboss.com
 * @since 01-Apr-2011
 *
 */
public class GZIPServletTestCase extends JBossWSTest
{
   public static Test suite()
   {
      return new JBossWSCXFTestSetup(GZIPServletTestCase.class, "jaxws-cxf-gzip.war, jaxws-cxf-gzip-client.war");
   }
   
   public void testGZIPUsingFeatureOnBus() throws Exception
   {
      assertEquals("1", runTestInContainer("testGZIPUsingFeatureOnBus"));
   }
   
   public void testGZIPUsingFeatureOnClient() throws Exception
   {
      assertEquals("1", runTestInContainer("testGZIPUsingFeatureOnClient"));
   }
   
   public void testGZIPServerSideOnlyInterceptorOnClient() throws Exception
   {
      assertEquals("1", runTestInContainer("testGZIPServerSideOnlyInterceptorOnClient"));
   }
   
   public void testFailureGZIPServerSideOnlyInterceptorOnClient() throws Exception
   {
      assertEquals("1", runTestInContainer("testFailureGZIPServerSideOnlyInterceptorOnClient"));
   }
   
   public void testGZIPServerSideOnlyInterceptorsOnBus() throws Exception
   {
      assertEquals("1", runTestInContainer("testGZIPServerSideOnlyInterceptorsOnBus"));
   }

   public void testFailureGZIPServerSideOnlyInterceptorsOnBus() throws Exception
   {
      assertEquals("1", runTestInContainer("testFailureGZIPServerSideOnlyInterceptorsOnBus"));
   }
   
   private String runTestInContainer(String test) throws Exception
   {
      URL url = new URL("http://" + getServerHost()
            + ":8080/jaxws-cxf-gzip-client?path=/jaxws-cxf-gzip/HelloWorldService/HelloWorldImpl&method=" + test
            + "&helper=" + Helper.class.getName());
      return IOUtils.readAndCloseStream(url.openStream());
   }
}
