package uk.gov.pay.directdebit.payments.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.directdebit.payments.api.PaymentRequestResponse;
import uk.gov.pay.directdebit.payments.api.PaymentRequestValidator;
import uk.gov.pay.directdebit.payments.services.PaymentRequestService;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.created;
import static uk.gov.pay.directdebit.common.resources.V1ApiPaths.ROOT_PATH;

@Path("/")
public class PaymentRequestResource {
    //have to be /charges unless we change public api
    public static final String CHARGE_API_PATH = ROOT_PATH +"/api/accounts/{accountId}/charges/{paymentRequestExternalId}";
    public static final String CHARGES_API_PATH = ROOT_PATH +"/api/accounts/{accountId}/charges";

    private static final Logger logger = LoggerFactory.getLogger(PaymentRequestResource.class);
    private final PaymentRequestService paymentRequestService;
    private final PaymentRequestValidator paymentRequestValidator = new PaymentRequestValidator();
    private final String ACCOUNT_ID = "accountId";

    public PaymentRequestResource(PaymentRequestService paymentRequestService) {
        this.paymentRequestService = paymentRequestService;
    }

    @GET
    @Path(CHARGE_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response getCharge(@PathParam(ACCOUNT_ID) Long accountId, @PathParam("paymentRequestExternalId") String paymentRequestExternalId, @Context UriInfo uriInfo) {
        PaymentRequestResponse response = paymentRequestService.getPaymentWithExternalId(paymentRequestExternalId, uriInfo);
        return Response.ok(response).build();
    }

    @POST
    @Path(CHARGES_API_PATH)
    @Produces(APPLICATION_JSON)
    public Response createNewPaymentRequest(@PathParam(ACCOUNT_ID) Long accountId, Map<String, String> paymentRequest, @Context UriInfo uriInfo) {
        paymentRequestValidator.validate(paymentRequest);
        logger.info("Creating new payment request - {}", paymentRequest.toString());
        PaymentRequestResponse response = paymentRequestService.create(paymentRequest, accountId, uriInfo);
        return created(response.getLink("self")).entity(response).build();
    }
}