/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.net.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.servlet.*;
import javax.servlet.http.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;


public class XSLTFilter implements Filter
{
  public static final Logger LOG = Logger.getLogger( XSLTFilter.class.getName() );

  private String xsltUrl;
  private String contentType;

  private Templates cachedTemplates;

  public void init( FilterConfig config )
    throws ServletException
  {
    this.xsltUrl = ServletHelper.getInitParameter( config, "xsltUrl", false );

    // Look for the xslt as a resource via the classloader, but if not
    // found, then just try it as a regular URL.
    URL u = this.getClass().getClassLoader().getResource( this.xsltUrl );
    
    if ( u != null )
      {
        this.xsltUrl = u.toString();
      }

    // Compile the template and cache it.
    try
      {
        LOG.info( "Loading XSL template: " + this.xsltUrl );
        this.cachedTemplates = TransformerFactory.newInstance( ).newTemplates( new StreamSource( xsltUrl ) );
      }
    catch ( javax.xml.transform.TransformerException te ) { throw new ServletException( te  ); }

    this.contentType = ServletHelper.getInitParameter( config, "contentType", "text/html; charset=utf-8" );    
  }

  public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain )
    throws IOException, ServletException 
  {
    if ( this.xsltUrl != null )
      {
        ByteArrayOutputStream baos = new ByteArrayOutputStream( 8 * 1024 );

        HttpServletResponseInterceptor capturedResponse = new HttpServletResponseInterceptor( (HttpServletResponse) response, baos );
        
        chain.doFilter( request, capturedResponse );
        
        byte output[] = baos.toByteArray( );
        
        try
          {
            Transformer transformer = getTemplates( (HttpServletRequest) request ).newTransformer( );
            
            StreamSource source = new StreamSource( new ByteArrayInputStream( output ) );
            StreamResult result = new StreamResult( response.getOutputStream( ) );
            
            // Enforce XML content-type in the response.
            response.setContentType( this.contentType );
            
            transformer.transform( source, result );
          }
        catch( javax.xml.transform.TransformerException te )
          {
            LOG.log( Level.SEVERE, "Error compiling XSL template", te );
            throw new ServletException( te );
          }
      }
    else
      {
        chain.doFilter( request, response );
      }
  }

  public void destroy()
  {

  }

  public Templates getTemplates( HttpServletRequest request )
    throws javax.xml.transform.TransformerConfigurationException
  {
    String header = null;
    if ( (header = request.getHeader( "pragma"        )) != null ||
         (header = request.getHeader( "cache-control" )) != null )
      {
        header = header.trim().toLowerCase( );
        
        if ( header.contains( "no-cache" ) )
          {
            LOG.info( "Reloading XSL template: " + this.xsltUrl );
            this.cachedTemplates = TransformerFactory.newInstance( ).newTemplates( new StreamSource( this.xsltUrl ) );
          }
      }

    return this.cachedTemplates;
  }

}


/**
 * Simple response wrapper that intercepts the response, writing it to
 * the given OutputStream.  It can be used to capture the response to
 * a byte[] by giving it an instance of ByteArrayOutputStream.
 */
class HttpServletResponseInterceptor extends HttpServletResponseWrapper
{
  private OutputStream os;

  HttpServletResponseInterceptor( HttpServletResponse response, OutputStream os )
  {
    super( response );
    
    this.os = os;
  }

  public ServletOutputStream getOutputStream() 
  {
    ServletOutputStream sos = new ServletOutputStream( )
      {
        public void write( int b )
          throws java.io.IOException
        {
          HttpServletResponseInterceptor.this.os.write( b );
        }
      };
    
    return sos;
  }

  public PrintWriter getWriter( )
  {
    PrintWriter pw = new PrintWriter( this.os );

    return pw;
  }

}