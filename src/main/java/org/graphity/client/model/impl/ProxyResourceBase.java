/**
 *  Copyright 2013 Martynas Jusevičius <martynas@graphity.org>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.graphity.client.model.impl;

import com.hp.hpl.jena.rdf.model.Model;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterface;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.core.ResourceContext;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Variant;
import org.graphity.core.MediaTypes;
import org.graphity.client.vocabulary.GC;
import org.graphity.client.util.DataManager;
import org.graphity.core.model.SPARQLEndpointFactory;
import org.graphity.core.model.SPARQLEndpointOrigin;
import org.graphity.core.model.impl.SPARQLEndpointOriginBase;
import org.graphity.core.provider.ModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource that can publish Linked Data and (X)HTML as well as load RDF from remote sources.
 * The remote datasources can either be native-RDF Linked Data, or formats supported by Locators
 * (for example, Atom XML transformed to RDF/XML using GRDDL XSLT stylesheet). The ability to load remote
 * RDF data is crucial for generic Linked Data browser functionality.
 * 
 * @author Martynas Jusevičius <martynas@graphity.org>
 * @see org.graphity.processor.locator.LocatorLinkedData
 * @see <a href="http://www.w3.org/TR/sparql11-query/#solutionModifiers">15 Solution Sequences and Modifiers</a>
 */
@Path("/")
public class ProxyResourceBase extends org.graphity.core.model.impl.ResourceBase
{
    private static final Logger log = LoggerFactory.getLogger(ProxyResourceBase.class);

    private final ResourceContext resourceContext;
    private final DataManager dataManager;
    private final MediaType mediaType;
    private final URI topicURI, endpointURI;
    private final WebResource webResource;
    //private final Application application;
    
    /**
     * JAX-RS compatible resource constructor with injected initialization objects.
     * 
     * @param uriInfo URI information of the request
     * @param request current request
     * @param servletConfig servlet config
     * @param resourceContext resource context
     * @param dataManager data manager for this resource
     */
    public ProxyResourceBase(@Context UriInfo uriInfo, @Context Request request, @Context ServletConfig servletConfig, @Context MediaTypes mediaTypes,
            @Context ResourceContext resourceContext, @Context DataManager dataManager)
    {
	super(uriInfo, request, servletConfig, mediaTypes);
	if (resourceContext == null) throw new IllegalArgumentException("ResourceContext cannot be null");
        if (dataManager == null) throw new IllegalArgumentException("DataManager cannot be null");
        //if (application == null) throw new IllegalArgumentException("Application cannot be null");
        this.resourceContext = resourceContext;
        this.dataManager = dataManager;
        //this.application = application;

	if (uriInfo.getQueryParameters().containsKey("accept"))
        {
            Map<String, String> utf8Param = new HashMap<>();
            utf8Param.put("charset", "UTF-8");            
            MediaType formatType = MediaType.valueOf(uriInfo.getQueryParameters().getFirst("accept"));
            mediaType = new MediaType(formatType.getType(), formatType.getSubtype(), utf8Param);
        }
        else mediaType = null;
        if (uriInfo.getQueryParameters().containsKey(GC.uri.getLocalName()))
        {
            topicURI = URI.create(uriInfo.getQueryParameters().getFirst(GC.uri.getLocalName()));
            ClientConfig cc = new DefaultClientConfig();
            cc.getSingletons().add(new ModelProvider());
            Client client = Client.create(cc);
            webResource = client.resource(topicURI); // org.graphity.core.MediaType.getRegistered()
        }
        else
        {
            topicURI = null;
            webResource = null;
        }
        if (uriInfo.getQueryParameters().containsKey(GC.endpointUri.getLocalName()))
            endpointURI = URI.create(uriInfo.getQueryParameters().getFirst(GC.endpointUri.getLocalName()));
        else endpointURI = null;

        /*
        URI classURI = null;
        List<String> links = httpHeaders.getRequestHeader("Link");
        if (links != null)
        {
            Iterator<String> it = links.iterator();
            Link link = Link.valueOf(it.next());
            if (link.getType().equals(RDF.type.getLocalName())) classURI = link.getHref();
        }
        if (classURI != null) matchedOntClass = OntDocumentManager.getInstance().getOntology(classURI.toString(), OntModelSpec.OWL_MEM);
        else matchedOntClass = null;
        */
        
        if (log.isDebugEnabled()) log.debug("Constructing GlobalResourceBase with MediaType: {} topic URI: {}", mediaType, topicURI);
    }

    public ResourceContext getResourceContext()
    {
        return resourceContext;
    }
    
    public DataManager getDataManager()
    {
        return dataManager;
    }

    /**
     * Returns URI of remote resource (<samp>uri</samp> query string parameter)
     * 
     * @return remote URI
     */
    public URI getTopicURI()
    {
	return topicURI;
    }

    /**
     * Returns media type requested by the client ("accept" query string parameter).
     * This mechanism overrides the normally used content negotiation.
     * 
     * @return media type parsed from query param
     */
    public MediaType getMediaType()
    {
	return mediaType;
    }

    /**
     * Returns URI of remote SPARQL endpoint (<samp>endpoint-uri</samp> query string parameter).
     * 
     * @return SPARQL endpoint URI
     */
    public URI getEndpointURI()
    {
        return endpointURI;
    }
    
    public WebResource getWebResource()
    {
        return webResource;
    }
    
    /**
     * Returns a list of supported RDF media types.
     * If media type is specified in query string,that type is used to serialize RDF representation.
     * Otherwise, normal content negotiation is used.
     * 
     * @return variant list
     */
    /*
    @Override
    public List<MediaType> getMediaTypes()
    {
	if (getMediaType() != null)
        {
            List<MediaType> list = new ArrayList<>();
            list.add(getMediaType());
            return list;
        }
        
        List<MediaType> list = getMediaTypes();
        Map<String, String> utf8Param = new HashMap<>();
        utf8Param.put("charset", "UTF-8");

        MediaType xhtmlXml = new MediaType(MediaType.APPLICATION_XHTML_XML_TYPE.getType(), MediaType.APPLICATION_XHTML_XML_TYPE.getSubtype(), utf8Param);
        list.add(0, xhtmlXml);

        if (getMode() != null && getMode().equals(URI.create(GC.MapMode.getURI())))
	{
	    if (log.isDebugEnabled()) log.debug("Mode is {}, returning 'text/html' media type as Google Maps workaround", getMode());
            MediaType textHtml = new MediaType(MediaType.TEXT_HTML_TYPE.getType(), MediaType.TEXT_HTML_TYPE.getSubtype(), utf8Param);
            list.add(0, textHtml);
	}
        
        return list;
    }
    */
    
    /**
     * Handles GET request and returns response with RDF description of this or remotely loaded resource.
     * If <samp>uri</samp> query string parameter is present, resource is loaded from the specified remote URI and
     * its RDF representation is returned. Otherwise, local resource with request URI is used.
     * 
     * @return response
     */
    @Override
    public Response get()
    {
        UniformInterface resource;
        
	if (getWebResource() != null)
	{
	    if (log.isDebugEnabled()) log.debug("Loading Model from URI: {}", getWebResource().getURI());
	    /*
            return getResponse(getWebResource().
                    accept(org.graphity.core.MediaType.TEXT_TURTLE, org.graphity.core.MediaType.APPLICATION_RDF_XML).
                    get(Model.class));
            */
            resource = getWebResource().
                    accept(org.graphity.core.MediaType.TEXT_TURTLE, org.graphity.core.MediaType.APPLICATION_RDF_XML);
	}
        /*
        else
        {
            resource = getResourceContext().getResource(ResourceBase.class);
        }
        */
        
	//return getResponse(resource.get(Model.class));
        return getResourceContext().getResource(ResourceBase.class).get();
    }

    @Override
    public Response post(Model model)
    {
	if (getWebResource() != null)
	{
	    if (log.isDebugEnabled()) log.debug("Submitting Model to URI: {}", getWebResource().getURI());
	    return getResponse(getWebResource().post(Model.class, model));
	}	

	return getResourceContext().
                getResource(ResourceBase.class).
                post(model); // super.post(model);
    }
    
    /**
     * Returns sub-resource instance.
     * By default matches any path.
     * 
     * @return resource object
     */
    @Path("{path: .+}")
    //@Override
    public Object getSubResource()
    {
        if (getEndpointURI() != null)
        {
            List<MediaType> mediaTypes = getMediaTypes().getModelMediaTypes();
            mediaTypes.addAll(getMediaTypes().getResultSetMediaTypes());
            List<Variant> variants = getResponse().getVariantListBuilder(mediaTypes, getLanguages(), getEncodings()).add().build();
            Variant variant = getRequest().selectVariant(variants);

            if (!variant.getMediaType().isCompatible(MediaType.TEXT_HTML_TYPE) &&
                    !variant.getMediaType().isCompatible(MediaType.APPLICATION_XHTML_XML_TYPE))
            {
                if (log.isDebugEnabled()) log.debug("Using remote SPARQL endpoint URI: {}", getEndpointURI());
                SPARQLEndpointOrigin origin = new SPARQLEndpointOriginBase(getEndpointURI().toString());
                return SPARQLEndpointFactory.createProxy(getRequest(), getServletConfig(), getMediaTypes(), origin, getDataManager());
            }
        }
        
        return this;
    }

    /*
    @Override
    public Resource createState(Resource state, Long offset, Long limit, String orderBy, Boolean desc, Resource mode)
    {
        Resource superState = super.createState(state, offset, limit, orderBy, desc, mode);

	if (getTopicURI() != null) superState.addProperty(GC.uri, state.getModel().createResource(getTopicURI().toString()));
	if (getEndpointURI() != null) superState.addProperty(GC.endpointUri, state.getModel().createResource(getEndpointURI().toString()));
        
        return state;
    }

    @Override
    public UriBuilder getStateUriBuilder(Long offset, Long limit, String orderBy, Boolean desc, URI mode)
    {
        UriBuilder builder = super.getStateUriBuilder(offset, limit, orderBy, desc, mode);
        
	if (getTopicURI() != null) builder.queryParam(GC.uri.getLocalName(), getTopicURI().toString());
	if (getEndpointURI() != null) builder.queryParam(GC.endpointUri.getLocalName(), getEndpointURI().toString());
	
	return builder;
    }
    */

    @Override
    public Response put(Model model)
    {
	if (getWebResource() != null)
	{
	    if (log.isDebugEnabled()) log.debug("Submitting Model to URI: {}", getWebResource().getURI());
	    return getResponse(getWebResource().put(Model.class, model));
	}	

	return getResourceContext().
                getResource(ResourceBase.class).
                put(model);
    }

    @Override
    public Response delete()
    {
        //getWebResource().delete(ClientResponse.class).
        Response temp = Client.create().resource(getTopicURI()).get(Response.class);
        
	if (getWebResource() != null)
	{
	    if (log.isDebugEnabled()) log.debug("Submitting Model to URI: {}", getWebResource().getURI());
	    //return getWebResource().delete(ClientResponse.class);
	}	

	return getResourceContext().
                getResource(ResourceBase.class).
                delete();
    }
    
}
