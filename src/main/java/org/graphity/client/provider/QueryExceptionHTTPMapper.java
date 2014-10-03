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
package org.graphity.client.provider;

import com.hp.hpl.jena.sparql.engine.http.QueryExceptionHTTP;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps (tunnels) Jena's remote query execution exception.
 * 
 * @author Martynas Jusevičius <martynas@graphity.org>
 */
@Provider
public class QueryExceptionHTTPMapper implements ExceptionMapper<QueryExceptionHTTP>
{

    @Override
    public Response toResponse(QueryExceptionHTTP qe)
    {
	return Response.
		status(qe.getResponseCode()).
		//entity(qe.getResponseMessage()). // setting entity disables default Tomcat error page
		build();
    }

}