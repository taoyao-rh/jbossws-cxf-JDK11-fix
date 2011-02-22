/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.wsf.stack.cxf.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.ws.Binding;
import javax.xml.ws.Endpoint;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.spi.ServiceDelegate;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.jaxws.ServiceImpl;
import org.jboss.logging.Logger;
import org.jboss.wsf.stack.cxf.client.configuration.JBossWSBusFactory;
import org.w3c.dom.Element;

/**
 * A custom javax.xml.ws.spi.Provider implementation
 * extending the CXF one while adding few customizations.
 * 
 * This also ensures a proper context classloader is set
 * (required on JBoss AS 7, as the TCCL does not include
 * implementation classes by default)
 * 
 * @author alessio.soldano@jboss.com
 * @since 27-Aug-2010
 *
 */
public class ProviderImpl extends org.apache.cxf.jaxws22.spi.ProviderImpl
{
   @Override
   protected org.apache.cxf.jaxws.EndpointImpl createEndpointImpl(Bus bus, String bindingId, Object implementor,
         WebServiceFeature... features)
   {
      Boolean db = (Boolean)bus.getProperty(Constants.DEPLOYMENT_BUS);
      if (db != null && db)
      {
         Logger.getLogger(ProviderImpl.class).info(
               "Cannot use the bus associated to the current deployment for starting a new endpoint, creating a new bus...");
         bus = BusFactory.newInstance().createBus();
      }
      return super.createEndpointImpl(bus, bindingId, implementor, features);
   }
   
   @Override
   public Endpoint createEndpoint(String bindingId, Object implementor) {
      ClassLoader origClassLoader = null;
      try
      {
         origClassLoader = checkAndFixContextClassLoader();
         return new DelegateEndpointImpl(super.createEndpoint(bindingId, implementor));
      }
      finally
      {
         if (origClassLoader != null)
            setContextClassLoader(origClassLoader);
      }
   }
   
   @Override
   public Endpoint createEndpoint(String bindingId,
         Object implementor,
         WebServiceFeature ... features) {
      ClassLoader origClassLoader = null;
      try
      {
         origClassLoader = checkAndFixContextClassLoader();
         return new DelegateEndpointImpl(super.createEndpoint(bindingId, implementor, features));
      }
      finally
      {
         if (origClassLoader != null)
            setContextClassLoader(origClassLoader);
      }
   }

   @SuppressWarnings("rawtypes")
   @Override
   public ServiceDelegate createServiceDelegate(URL url, QName qname, Class cls)
   {
      ClassLoader origClassLoader = null;
      try
      {
         origClassLoader = checkAndFixContextClassLoader();
         //we override this method to prevent using the default bus when the current
         //thread is not already associated to a bus. In those situations we create
         //a new bus from scratch instead and link that to the thread.
         Bus bus = BusFactory.getThreadDefaultBus(false);
         if (bus == null)
         {
            bus = BusFactory.newInstance().createBus(); //this also set thread local bus internally as it's not set yet
         }
         return new ServiceImpl(bus, url, qname, cls);
      }
      finally
      {
         if (origClassLoader != null)
            setContextClassLoader(origClassLoader);
      }
   }

   @SuppressWarnings("rawtypes")
   @Override
   public ServiceDelegate createServiceDelegate(URL wsdlDocumentLocation, QName serviceName, Class serviceClass,
         WebServiceFeature... features)
   {
      ClassLoader origClassLoader = null;
      try
      {
         origClassLoader = checkAndFixContextClassLoader();
         //we override this method to prevent using the default bus when the current
         //thread is not already associated to a bus. In those situations we create
         //a new bus from scratch instead and link that to the thread.
         Bus bus = BusFactory.getThreadDefaultBus(false);
         if (bus == null)
         {
            bus = BusFactory.newInstance().createBus(); //this also set thread local bus internally as it's not set yet 
         }
         return super.createServiceDelegate(wsdlDocumentLocation, serviceName, serviceClass, features);
      }
      finally
      {
         if (origClassLoader != null)
            setContextClassLoader(origClassLoader);
      }
   }
   
   /**
    * Ensure the current context classloader can load this ProviderImpl class.
    * 
    * @return The original classloader or null if it's not been changed
    */
   private static ClassLoader checkAndFixContextClassLoader()
   {
      ClassLoader origClassLoader = getContextClassLoader();
      try
      {
         origClassLoader.loadClass(ProviderImpl.class.getName());
      }
      catch (Exception e)
      {
         //[JBWS-3223] On AS7 the TCCL that's set for basic (non-ws-endpoint) servlet/ejb3
         //apps doesn't have visibility on any WS implementation class, so Apache CXF
         //can't load its components through it - we need to change the TCCL using
         //the classloader that has been used to load this javax.xml.ws.spi.Provider impl.
         ClassLoader clientClassLoader = ProviderImpl.class.getClassLoader();
         
         //first ensure the default bus is loaded through the client classloader only
         //(no deployment classloader contribution)
         if (BusFactory.getDefaultBus(false) == null)
         {
            JBossWSBusFactory.getDefaultBus(clientClassLoader);
         }
         //then setup a new TCCL having visibility over both the client path (JBossWS
         //client module on AS7) and the the former TCCL (i.e. the deployment classloader)
         setContextClassLoader(new DelegateClassLoader(clientClassLoader, origClassLoader));
         return origClassLoader;
      }
      return null;
   }

   /**
    * Get context classloader.
    *
    * @return the current context classloader
    */
   static ClassLoader getContextClassLoader()
   {
      SecurityManager sm = System.getSecurityManager();
      if (sm == null)
      {
         return Thread.currentThread().getContextClassLoader();
      }
      else
      {
         return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>()
         {
            public ClassLoader run()
            {
               return Thread.currentThread().getContextClassLoader();
            }
         });
      }
   }
   
   /**
    * Set context classloader.
    *
    * @param classLoader the classloader
    */
   static void setContextClassLoader(final ClassLoader classLoader)
   {
      if (System.getSecurityManager() == null)
      {
         Thread.currentThread().setContextClassLoader(classLoader);
      }
      else
      {
         AccessController.doPrivileged(new PrivilegedAction<Object>()
         {
            public Object run()
            {
               Thread.currentThread().setContextClassLoader(classLoader);
               return null;
            }
         });
      }
   }
   
   /**
    * A javax.xml.ws.Endpoint implementation delegating to a provided one
    * that sets the TCCL before doing publish.
    * 
    */
   private static final class DelegateEndpointImpl extends Endpoint
   {
      private Endpoint delegate;
      
      public DelegateEndpointImpl(Endpoint delegate)
      {
         this.delegate = delegate;
      }
      
      @Override
      public Binding getBinding()
      {
         return delegate.getBinding();
      }

      @Override
      public Object getImplementor()
      {
         return delegate.getImplementor();
      }

      @Override
      public void publish(String address)
      {
         ClassLoader origClassLoader = null;
         try
         {
            origClassLoader = checkAndFixContextClassLoader();
            delegate.publish(address);
         }
         finally
         {
            if (origClassLoader != null)
               setContextClassLoader(origClassLoader);
         }
      }

      @Override
      public void publish(Object serverContext)
      {
         ClassLoader origClassLoader = null;
         try
         {
            origClassLoader = checkAndFixContextClassLoader();
            delegate.publish(serverContext);
         }
         finally
         {
            if (origClassLoader != null)
               setContextClassLoader(origClassLoader);
         }
      }

      @Override
      public void stop()
      {
         delegate.stop();
      }

      @Override
      public boolean isPublished()
      {
         return delegate.isPublished();
      }

      @Override
      public List<Source> getMetadata()
      {
         return delegate.getMetadata();
      }

      @Override
      public void setMetadata(List<Source> metadata)
      {
         delegate.setMetadata(metadata);
      }

      @Override
      public Executor getExecutor()
      {
         return delegate.getExecutor();
      }

      @Override
      public void setExecutor(Executor executor)
      {
         delegate.setExecutor(executor);
      }

      @Override
      public Map<String, Object> getProperties()
      {
         return delegate.getProperties();
      }

      @Override
      public void setProperties(Map<String, Object> properties)
      {
         delegate.setProperties(properties);
      }

      @Override
      public EndpointReference getEndpointReference(Element... referenceParameters)
      {
         return delegate.getEndpointReference(referenceParameters);
      }

      @Override
      public <T extends EndpointReference> T getEndpointReference(Class<T> clazz, Element... referenceParameters)
      {
         return delegate.getEndpointReference(clazz, referenceParameters);
      }
   }

   private static final class DelegateClassLoader extends SecureClassLoader
   {
      private ClassLoader delegate;

      private ClassLoader parent;

      public DelegateClassLoader(final ClassLoader delegate, final ClassLoader parent)
      {
         super(parent);
         this.delegate = delegate;
         this.parent = parent;
      }

      /** {@inheritDoc} */
      @Override
      public Class<?> loadClass(final String className) throws ClassNotFoundException
      {
         if (parent != null)
         {
            try
            {
               return parent.loadClass(className);
            }
            catch (ClassNotFoundException cnfe)
            {
               //NOOP, use delegate
            }
         }
         return delegate.loadClass(className);
      }

      /** {@inheritDoc} */
      @Override
      public URL getResource(final String name)
      {
         URL url = null;
         if (parent != null)
         {
            url = parent.getResource(name);
         }
         return (url == null) ? delegate.getResource(name) : url;
      }

      /** {@inheritDoc} */
      @Override
      public Enumeration<URL> getResources(final String name) throws IOException
      {
         final ArrayList<Enumeration<URL>> foundResources = new ArrayList<Enumeration<URL>>();

         foundResources.add(delegate.getResources(name));
         foundResources.add(parent.getResources(name));

         return new Enumeration<URL>()
         {
            private int position = foundResources.size() - 1;

            public boolean hasMoreElements()
            {
               while (position >= 0)
               {
                  if (foundResources.get(position).hasMoreElements())
                  {
                     return true;
                  }
                  position--;
               }
               return false;
            }

            public URL nextElement()
            {
               while (position >= 0)
               {
                  try
                  {
                     return (foundResources.get(position)).nextElement();
                  }
                  catch (NoSuchElementException e)
                  {
                  }
                  position--;
               }
               throw new NoSuchElementException();
            }
         };
      }

      /** {@inheritDoc} */
      @Override
      public InputStream getResourceAsStream(final String name)
      {
         URL foundResource = getResource(name);
         if (foundResource != null)
         {
            try
            {
               return foundResource.openStream();
            }
            catch (IOException e)
            {
            }
         }
         return null;
      }
   };
}
