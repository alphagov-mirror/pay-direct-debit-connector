package uk.gov.pay.directdebit.healthcheck.resources;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.dropwizard.setup.Environment;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Path("/")
public class HealthCheckResource {

    private Environment environment;

    @Inject
    public HealthCheckResource(Environment environment) {
        this.environment = environment;
    }

    @GET
    @Path("healthcheck")
    @Produces(APPLICATION_JSON)
    @Timed
    public Response healthCheck() throws JsonProcessingException {
        SortedMap<String, HealthCheck.Result> results = environment.healthChecks().runHealthChecks();

        Map<String, Map<String, Object>> response = getResponse(results);

        boolean healthy = results.size() == results.values()
                .stream()
                .filter(HealthCheck.Result::isHealthy)
                .count();

        if (healthy) {
            return Response.ok().entity(response).build();
        }
        return status(503).entity(response).build();
    }

    private Map<String, Map<String, Object>> getResponse(SortedMap<String, HealthCheck.Result> results) {
        Map<String, Map<String, Object>> response = new HashMap<>();
        for (SortedMap.Entry<String, HealthCheck.Result> entry : results.entrySet()) {
            response.put(entry.getKey(), ImmutableMap.of(
                    "healthy", entry.getValue().isHealthy(),
                    "message", isBlank(entry.getValue().getMessage()) ? "Healthy" : entry.getValue().getMessage()));
        }
        return response;
    }
}
