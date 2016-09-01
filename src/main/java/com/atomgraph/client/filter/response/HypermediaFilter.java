/*
 * Copyright 2015 Martynas Jusevičius <martynas@atomgraph.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.atomgraph.client.filter.response;

import org.apache.jena.ontology.AnnotationProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.vocabulary.RDF;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.riot.checker.CheckerIRI;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.vocabulary.RDFS;
import com.atomgraph.client.exception.OntClassNotFoundException;
import com.atomgraph.client.util.OntologyProvider;
import com.atomgraph.client.vocabulary.GC;
import com.atomgraph.client.vocabulary.LDT;
import com.atomgraph.core.exception.ConfigurationException;
import com.atomgraph.core.util.Link;
import com.atomgraph.core.util.StateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Martynas Jusevičius <martynas@atomgraph.com>
 */
@Provider
public class HypermediaFilter implements ContainerResponseFilter
{
    private static final Logger log = LoggerFactory.getLogger(HypermediaFilter.class);
   
    @Override
    public ContainerResponse filter(ContainerRequest request, ContainerResponse response)
    {
        if (request == null) throw new IllegalArgumentException("ContainerRequest cannot be null");
        if (response == null) throw new IllegalArgumentException("ContainerResponse cannot be null");

        if (!response.getStatusType().getFamily().equals(Response.Status.Family.SUCCESSFUL)) return response;
        
        Model model = getModel(response.getEntity());
        if (model == null) return response;

        Resource requestUri = model.createResource(request.getRequestUri().toString()); //getState(request, model);

        try
        {
            MultivaluedMap<String, Object> headerMap = response.getHttpHeaders();
            URI ontologyHref = getOntologyURI(headerMap);
            URI typeHref = getTypeURI(headerMap);
            if (ontologyHref == null || typeHref == null) return response;

            OntModelSpec ontModelSpec = getOntModelSpec(getRules(headerMap, "Rules"));            
            OntModel ontModel = getOntModel(ontologyHref.toString(), ontModelSpec);

            long oldCount = model.size();
            IRI forClassIRI = getForClassIRI(request, ontModel);            
            if (forClassIRI != null)
            {
                OntClass forClass = ontModel.getOntClass(forClassIRI.toURI().toString());
                if (forClass == null) throw new OntClassNotFoundException("OntClass '" + forClassIRI + "' not found in sitemap");

                ResIterator it = model.listResourcesWithProperty(GC.forClass);                
                try
                {
                    Resource constructorRes = it.next();
                    //requestUri.addProperty(GC.constructorOf, getForClassBuilder(requestUri, null).build()). // connects constructor state to its container
                    constructorRes.addProperty(GC.constructor, addInstance(model, forClass)); // connects constructor state to CONSTRUCTed template
                }
                finally
                {
                    it.close();
                }
            }
            else
            {
                // layout modes only apply to XHTML media type
                if (response.getMediaType() == null ||
                        !(response.getMediaType().isCompatible(MediaType.APPLICATION_XHTML_XML_TYPE) ||
                        response.getMediaType().isCompatible(MediaType.TEXT_HTML_TYPE)))
                    return response;

                OntClass template = ontModel.getOntClass(typeHref.toString());
                if (template == null) return response;

                Resource defaultMode = template.getPropertyResourceValue(GC.defaultMode);
                if (defaultMode != null)
                {
                    // transition to a URI of another application state (HATEOAS)
                    Resource defaultState = StateBuilder.fromResource(requestUri).
                        replaceProperty(GC.mode, defaultMode).
                        build();
                    if (!defaultState.equals(requestUri))
                    {
                        if (log.isDebugEnabled()) log.debug("Redirecting to a state transition URI: {}", defaultState.getURI());
                        response.setResponse(Response.seeOther(URI.create(defaultState.getURI())).build());
                        return response;
                    }
                }

                // TO-DO: use all individuals of type gc:Mode?
                addLayouts(StateBuilder.fromResource(requestUri).replaceProperty(GC.mode, null).build(), template);
            }
            
            if (log.isDebugEnabled()) log.debug("Added HATEOAS transitions to the response RDF Model for resource: {} # of statements: {}", requestUri.getURI(), model.size() - oldCount);
            response.setEntity(model);
        }
        catch (URISyntaxException ex)
        {
            return response;
        }

        return response;
    }
    
    public Model getModel(Object entity)
    {
        if (entity instanceof Model) return (Model)entity;
        
        return null;
    }

    /*
    public StateBuilder getForClassBuilder(Resource resource, OntClass forClass)
    {
        if (resource == null) throw new IllegalArgumentException("Resource cannot be null");

        return StateBuilder.fromUri(resource.getURI(), resource.getModel()).
            replaceProperty(GC.forClass, forClass);
    }
    
    public StateBuilder getModeBuilder(StateBuilder sb, TemplateCall templateCall)
    {
        if (sb == null) throw new IllegalArgumentException("Resource cannot be null");
        if (templateCall == null) throw new IllegalArgumentException("TemplateCall cannot be null");

        if (templateCall.hasProperty(GC.mode)) sb.replaceProperty(GC.mode, templateCall.getProperty(GC.mode).getObject());
        
        return sb;
    }
    */

    public final Resource getResource(OntClass ontClass, AnnotationProperty property)
    {
        if (ontClass.hasProperty(property) && ontClass.getPropertyValue(property).isResource())
            return ontClass.getPropertyValue(property).asResource();
        
        return null;
    }
    
    public void addLayouts(Resource state, OntClass template)
    {
        if (state == null) throw new IllegalArgumentException("Resource cannot be null");
        if (template == null) throw new IllegalArgumentException("OntClass cannot be null");
        
        NodeIterator it = template.listPropertyValues(GC.supportedMode);
        try
        {
            while (it.hasNext())
            {
                RDFNode supportedMode = it.next();
                if (!supportedMode.isURIResource())
                {
                    if (log.isErrorEnabled()) log.error("Invalid Mode defined for template '{}' (gc:supportedMode)", template.getURI());
                    throw new ConfigurationException("Invalid Mode defined for template '" + template.getURI() +"'");
                }

                StateBuilder.fromResource(state).
                    replaceProperty(GC.mode, supportedMode.asResource()).
                    build().
                    addProperty(GC.layoutOf, state);
            }
        }
        finally
        {
            it.close();
        }
    }

    public URI getTypeURI(MultivaluedMap<String, Object> headerMap) throws URISyntaxException
    {
        return getLinkHref(headerMap, "Link", RDF.type.getLocalName());
    }

    public URI getOntologyURI(MultivaluedMap<String, Object> headerMap) throws URISyntaxException
    {
        return getLinkHref(headerMap, "Link", LDT.ontology.getURI());
    }
    
    public URI getLinkHref(MultivaluedMap<String, Object> headerMap, String headerName, String rel) throws URISyntaxException
    {
	if (headerMap == null) throw new IllegalArgumentException("Header Map cannot be null");
	if (headerName == null) throw new IllegalArgumentException("String header name cannot be null");
        if (rel == null) throw new IllegalArgumentException("Property Map cannot be null");
        
        List<Object> links = headerMap.get(headerName);
        if (links != null)
        {
            Iterator<Object> it = links.iterator();
            while (it.hasNext())
            {
                String linkHeader = it.next().toString();
                Link link = Link.valueOf(linkHeader);
                if (link.getRel().equals(rel)) return link.getHref();
            }
        }
        
        return null;
    }
    
    public OntModelSpec getOntModelSpec(List<Rule> rules)
    {
        OntModelSpec ontModelSpec = new OntModelSpec(OntModelSpec.OWL_MEM);
        
        if (rules != null)
        {
            Reasoner reasoner = new GenericRuleReasoner(rules);
            //reasoner.setDerivationLogging(true);
            //reasoner.setParameter(ReasonerVocabulary.PROPtraceOn, Boolean.TRUE);
            ontModelSpec.setReasoner(reasoner);
        }
        
        return ontModelSpec;
    }
    
    public final List<Rule> getRules(MultivaluedMap<String, Object> headerMap, String headerName)
    {
        String rules = getRulesString(headerMap, headerName);
        if (rules == null) return null;
        
        return Rule.parseRules(rules);
    }
    
    public String getRulesString(MultivaluedMap<String, Object> headerMap, String headerName)
    {
	if (headerMap == null) throw new IllegalArgumentException("Header Map cannot be null");
	if (headerName == null) throw new IllegalArgumentException("String header name cannot be null");

        Object rules = headerMap.getFirst(headerName);
        if (rules != null) return rules.toString();
        
        return null;
    }
    
    public OntModel getOntModel(String ontologyURI, OntModelSpec ontModelSpec)
    {
        return new OntologyProvider().getOntModel(ontologyURI, ontModelSpec);
    }
  
    public Resource getState(ContainerRequest request, Model model)
    {
	if (request == null) throw new IllegalArgumentException("ContainerRequest cannot be null");
        if (model == null) throw new IllegalArgumentException("Model cannot be null");
        
        StateBuilder sb = StateBuilder.fromUri(request.getRequestUri(), model).
            replaceProperty(GC.uri, model.createResource(request.getQueryParameters().getFirst(GC.uri.getLocalName())));
        if (request.getQueryParameters().containsKey(GC.mode.getLocalName()))
            sb.replaceProperty(GC.mode, model.createResource(request.getQueryParameters().getFirst(GC.mode.getLocalName())));
        if (request.getQueryParameters().containsKey(GC.forClass.getLocalName()))
            sb.replaceProperty(GC.forClass, model.createResource(request.getQueryParameters().getFirst(GC.forClass.getLocalName())));

        return sb.build();
    }

    public Resource getMode(ContainerRequest request, OntClass template)
    {
	if (request == null) throw new IllegalArgumentException("ContainerRequest cannot be null");
        if (template == null) throw new IllegalArgumentException("OntClass cannot be null");

        final Resource mode;
        if (request.getQueryParameters().containsKey(GC.mode.getLocalName()))
            mode = ResourceFactory.createResource(request.getQueryParameters().getFirst(GC.mode.getLocalName()));
        else mode = getResource(template, GC.defaultMode);
        
        return mode;
    }

    public IRI getForClassIRI(ContainerRequest request, OntModel ontModel)
    {
	if (request == null) throw new IllegalArgumentException("ContainerRequest cannot be null");
	if (ontModel == null) throw new IllegalArgumentException("OntModel cannot be null");

        if (request.getQueryParameters().containsKey(GC.forClass.getLocalName()))
        {
            String classIRIStr = request.getQueryParameters().getFirst(GC.forClass.getLocalName());
            IRI classIRI = IRIFactory.iriImplementation().create(classIRIStr);
            // throws Exceptions on bad URIs:
            CheckerIRI.iriViolations(classIRI, ErrorHandlerFactory.getDefaultErrorHandler());

            return classIRI;
        }
        
        return null;
    }
    
    public Resource addInstance(Model targetModel, OntClass forClass)
    {
        if (log.isDebugEnabled()) log.debug("Invoking constructor on class: {}", forClass);
        addClass(forClass, targetModel); // TO-DO: remove when classes and constraints are cached/dereferencable
        return new ConstructorBase().construct(forClass, targetModel);
    }
    
    // TO-DO: this method should not be necessary when system ontologies/classes are dereferencable! -->
    public void addClass(OntClass forClass, Model targetModel)
    {
        if (forClass == null) throw new IllegalArgumentException("OntClass cannot be null");
        if (targetModel == null) throw new IllegalArgumentException("Model cannot be null");    

        String queryString = "PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
"PREFIX  spin: <http://spinrdf.org/spin#>\n" +
"\n" +
"DESCRIBE ?Class ?Constraint\n" +
"WHERE\n" +
"  { ?Class rdfs:isDefinedBy ?Ontology\n" +
"    OPTIONAL\n" +
"      { ?Class spin:constraint ?Constraint }\n" +
"  }";
        
        // the client needs at least labels and constraints
        QuerySolutionMap qsm = new QuerySolutionMap();
        qsm.add(RDFS.Class.getLocalName(), forClass);
        Query query = new ParameterizedSparqlString(queryString, qsm).asQuery();
        QueryExecution qex = QueryExecutionFactory.create(query, forClass.getOntModel());
        try
        {
            targetModel.add(qex.execDescribe());
        }
        finally
        {
            qex.close();
        }
    }
    
}