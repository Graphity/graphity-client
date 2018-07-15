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

package com.atomgraph.client.mapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import com.atomgraph.client.exception.ClientErrorException;
import org.apache.jena.rdf.model.Resource;

/**
 *
 * @author Martynas Jusevičius <martynas@atomgraph.com>
 */
@Provider
public class ClientErrorExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<ClientErrorException>
{
    
    @Override
    public Response toResponse(ClientErrorException ex)
    {
        Resource exRes = toResource(ex, Response.Status.fromStatusCode(ex.getClientResponse().getStatus()), null);
        
        if (ex.getModel() != null) exRes.getModel().add(ex.getModel()); // tunnel exception Model, e.g. with RequestAccess
        
        return com.atomgraph.core.model.impl.Response.fromRequest(getRequest()).
            getResponseBuilder(exRes.getModel(), getVariants()).
            status(ex.getClientResponse().getStatus()).
            build();
    }
    
}
