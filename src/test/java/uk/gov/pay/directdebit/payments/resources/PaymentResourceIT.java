package uk.gov.pay.directdebit.payments.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.commons.testing.port.PortFactory;
import uk.gov.pay.directdebit.gatewayaccounts.model.PaymentProvider;
import uk.gov.pay.directdebit.junit.DropwizardAppWithPostgresRule;
import uk.gov.pay.directdebit.junit.TestContext;
import uk.gov.pay.directdebit.mandate.fixtures.MandateFixture;
import uk.gov.pay.directdebit.mandate.model.GoCardlessMandateId;
import uk.gov.pay.directdebit.mandate.model.Mandate;
import uk.gov.pay.directdebit.mandate.model.MandateState;
import uk.gov.pay.directdebit.mandate.model.SandboxMandateId;
import uk.gov.pay.directdebit.payers.fixtures.PayerFixture;
import uk.gov.pay.directdebit.payments.fixtures.GatewayAccountFixture;
import uk.gov.pay.directdebit.payments.fixtures.PaymentFixture;
import uk.gov.pay.directdebit.payments.model.PaymentState;

import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.directdebit.gatewayaccounts.model.PaymentProvider.GOCARDLESS;
import static uk.gov.pay.directdebit.gatewayaccounts.model.PaymentProvider.SANDBOX;
import static uk.gov.pay.directdebit.mandate.fixtures.MandateFixture.aMandateFixture;
import static uk.gov.pay.directdebit.payers.fixtures.PayerFixture.aPayerFixture;
import static uk.gov.pay.directdebit.payments.fixtures.PaymentFixture.aPaymentFixture;
import static uk.gov.pay.directdebit.payments.resources.PaymentResource.CHARGE_API_PATH;
import static uk.gov.pay.directdebit.util.GoCardlessStubs.stubCreatePayment;
import static uk.gov.pay.directdebit.util.GoCardlessStubs.stubGetCreditor;
import static uk.gov.pay.directdebit.util.NumberMatcher.isNumber;

public class PaymentResourceIT {

    private static final String FRONTEND_CARD_DETAILS_URL = "/secure";
    private static final String JSON_AMOUNT_KEY = "amount";
    private static final String JSON_REFERENCE_KEY = "reference";
    private static final String JSON_DESCRIPTION_KEY = "description";
    private static final String JSON_GATEWAY_ACC_KEY = "gateway_account_id";
    private static final String JSON_RETURN_URL_KEY = "return_url";
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_PAYMENT_ID_KEY = "payment_id";
    private static final String JSON_PROVIDER_ID_KEY = "provider_id";
    private static final String JSON_MANDATE_ID_KEY = "mandate_id";
    private static final String JSON_PAYMENT_PROVIDER_KEY = "payment_provider";
    private static final String JSON_STATE_STATUS_KEY = "state.status";
    private static final String JSON_STATE_DETAILS_KEY = "state.details";
    private static final long AMOUNT = 6234L;
    private GatewayAccountFixture testGatewayAccount;
    private TestContext testContext;

    private int wireMockRulePort = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRuleGoCardless = new WireMockRule(wireMockRulePort);

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private WireMockServer wireMockAdminUsers = new WireMockServer(options().port(10110));

    @After
    public void tearDown() {
        wireMockAdminUsers.shutdown();
        app.getDatabaseTestHelper().truncateAllData();
    }

    @Before
    public void setUp() {
        testContext = app.getTestContext();
        wireMockAdminUsers.start();
        testGatewayAccount = GatewayAccountFixture.aGatewayAccountFixture().insert(testContext.getJdbi());
    }

    @Test
    public void shouldCollectAPayment_forSandbox() throws Exception {
        PayerFixture payerFixture = aPayerFixture();
        MandateFixture mandateFixture = aMandateFixture()
                .withGatewayAccountFixture(testGatewayAccount)
                .withPayerFixture(payerFixture)
                .withPaymentProviderId(SandboxMandateId.valueOf("sandbox-mandate-id"))
                .withState(MandateState.ACTIVE)
                .insert(testContext.getJdbi());
        String accountExternalId = testGatewayAccount.getExternalId();
        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = new ObjectMapper().writeValueAsString(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountExternalId)
                .put(JSON_MANDATE_ID_KEY, mandateFixture.getExternalId().toString())
                .build());

        String requestPath = "/v1/api/accounts/{accountId}/charges/collect"
                .replace("{accountId}", accountExternalId);
        String lastTwoDigitsBankAccount = payerFixture.getAccountNumber().substring(payerFixture.getAccountNumber().length() - 2);
        String chargeDate = LocalDate.now().plusDays(4).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        // language=JSON
        String emailPayloadBody = "{\n" +
                "  \"address\": \"" + payerFixture.getEmail() + "\",\n" +
                "  \"gateway_account_external_id\": \"" + testGatewayAccount.getExternalId() + "\",\n" +
                "  \"template\": \"ON_DEMAND_PAYMENT_CONFIRMED\",\n" +
                "  \"personalisation\": {\n" +
                "    \"amount\": \"" + BigDecimal.valueOf(AMOUNT, 2).toString() + "\",\n" +
                "    \"mandate reference\": \"" + mandateFixture.getMandateReference() + "\",\n" +
                "    \"bank account last 2 digits\": \"" + lastTwoDigitsBankAccount + "\",\n" +
                "    \"collection date\": \"" + chargeDate + "\",\n" +
                "    \"statement name\": \"Sandbox SUN Name\",\n" +
                "    \"dd guarantee link\": \"http://Frontend/direct-debit-guarantee\"\n" +
                "  }\n" +
                "}";

        wireMockAdminUsers.stubFor(post(urlPathEqualTo("/v1/emails/send"))
                .withRequestBody(equalToJson(emailPayloadBody))
                .willReturn(
                        aResponse().withStatus(200)));

        ValidatableResponse response = givenSetup()
                .body(postBody)
                .post(requestPath)
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .body(JSON_PAYMENT_ID_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_MANDATE_ID_KEY, is(mandateFixture.getExternalId().toString()))
                .body(JSON_STATE_STATUS_KEY, is("pending"))
                .body(JSON_PROVIDER_ID_KEY, is(notNullValue()))
                .body(JSON_PAYMENT_PROVIDER_KEY, is(SANDBOX.toString().toLowerCase(Locale.ENGLISH)))
                .contentType(JSON);

        String externalTransactionId = response.extract().path(JSON_PAYMENT_ID_KEY).toString();

        Map<String, Object> createdTransaction = testContext.getDatabaseTestHelper().getPaymentByExternalId(externalTransactionId);
        assertThat(createdTransaction.get("external_id"), is(notNullValue()));
        assertThat(createdTransaction.get("reference"), is(expectedReference));
        assertThat(createdTransaction.get("description"), is(expectedDescription));
        assertThat(createdTransaction.get("amount"), is(AMOUNT));
    }

    @Test
    public void shouldCollectAPayment_withNoDescription() throws Exception {
        PayerFixture payerFixture = aPayerFixture();
        MandateFixture mandateFixture = aMandateFixture()
                .withGatewayAccountFixture(testGatewayAccount)
                .withPayerFixture(payerFixture)
                .withPaymentProviderId(SandboxMandateId.valueOf("sandbox-mandate-id"))
                .withState(MandateState.ACTIVE)
                .insert(testContext.getJdbi());
        String accountExternalId = testGatewayAccount.getExternalId();
        String expectedReference = "Test reference";
        String postBody = new ObjectMapper().writeValueAsString(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_GATEWAY_ACC_KEY, accountExternalId)
                .put(JSON_MANDATE_ID_KEY, mandateFixture.getExternalId().toString())
                .build());

        String requestPath = "/v1/api/accounts/{accountId}/charges/collect"
                .replace("{accountId}", accountExternalId);

        givenSetup()
                .body(postBody)
                .post(requestPath)
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .body("$", not(hasKey(JSON_DESCRIPTION_KEY)))
                .contentType(JSON);
    }

    @Test
    public void shouldCollectAPayment_forGoCardless() throws Exception {
        GatewayAccountFixture gatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(GOCARDLESS).insert(testContext.getJdbi());
        PayerFixture payerFixture = aPayerFixture();
        GoCardlessMandateId goCardlessMandate = GoCardlessMandateId.valueOf("aGoCardlessMandateId");
        Mandate mandate = aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withPaymentProviderId(goCardlessMandate)
                .withPayerFixture(payerFixture)
                .withState(MandateState.ACTIVE)
                .insert(testContext.getJdbi())
                .toEntity();
        
        String accountExternalId = gatewayAccountFixture.getExternalId();
        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = new ObjectMapper().writeValueAsString(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountExternalId)
                .put(JSON_MANDATE_ID_KEY, mandate.getExternalId().toString())
                .build());

        String sunName = "Test SUN Name";
        String requestPath = "/v1/api/accounts/{accountId}/charges/collect"
                .replace("{accountId}", accountExternalId);
        stubCreatePayment(gatewayAccountFixture.getAccessToken().toString(), AMOUNT, goCardlessMandate, null);
        String lastTwoDigitsBankAccount = payerFixture.getAccountNumber().substring(payerFixture.getAccountNumber().length() - 2);
        stubGetCreditor(gatewayAccountFixture.getAccessToken().toString(), sunName);
        // language=JSON
        String emailPayloadBody = "{\n" +
                "  \"address\": \"" + payerFixture.getEmail() + "\",\n" +
                "  \"gateway_account_external_id\": \"" + gatewayAccountFixture.getExternalId() + "\",\n" +
                "  \"template\": \"ON_DEMAND_PAYMENT_CONFIRMED\",\n" +
                "  \"personalisation\": {\n" +
                "    \"amount\": \"" + BigDecimal.valueOf(AMOUNT, 2).toString() + "\",\n" +
                "    \"mandate reference\": \"" + mandate.getMandateBankStatementReference() + "\",\n" +
                "    \"bank account last 2 digits\": \"" + lastTwoDigitsBankAccount + "\",\n" +
                "    \"collection date\": \"21/05/2014\",\n" +
                "    \"statement name\": \"" + sunName + "\",\n" +
                "    \"dd guarantee link\": \"http://Frontend/direct-debit-guarantee\"\n" +
                "  }\n" +
                "}";

        wireMockAdminUsers.stubFor(post(urlPathEqualTo("/v1/emails/send"))
                .withRequestBody(equalToJson(emailPayloadBody))
                .willReturn(
                        aResponse().withStatus(200)));

        ValidatableResponse response = givenSetup()
                .body(postBody)
                .post(requestPath)
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .body(JSON_PAYMENT_ID_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_MANDATE_ID_KEY, is(mandate.getExternalId().toString()))
                .body(JSON_STATE_STATUS_KEY, is("pending"))
                .body(JSON_PROVIDER_ID_KEY, is(notNullValue()))
                .body(JSON_PAYMENT_PROVIDER_KEY, is(GOCARDLESS.toString().toLowerCase(Locale.ENGLISH)))
                .contentType(JSON);
        
        String externalTransactionId = response.extract().path(JSON_PAYMENT_ID_KEY).toString();

        Map<String, Object> createdTransaction = testContext.getDatabaseTestHelper().getPaymentByExternalId(externalTransactionId);
        assertThat(createdTransaction.get("external_id"), is(notNullValue()));
        assertThat(createdTransaction.get("reference"), is(expectedReference));
        assertThat(createdTransaction.get("description"), is(expectedDescription));
        assertThat(createdTransaction.get("amount"), is(AMOUNT));

        String getRequestPath = "/v1/api/accounts/{accountId}/charges/{paymentExternalId}"
                .replace("{accountId}", accountExternalId)
                .replace("{paymentExternalId}", externalTransactionId);
        response = givenSetup()
                .get(getRequestPath)
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(JSON_PAYMENT_ID_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_MANDATE_ID_KEY, is(mandate.getExternalId().toString()))
                .body(JSON_STATE_STATUS_KEY, is("pending"))
                .body(JSON_PROVIDER_ID_KEY, is(notNullValue()))
                .body(JSON_PAYMENT_PROVIDER_KEY, is(GOCARDLESS.toString().toLowerCase(Locale.ENGLISH)))
                .contentType(JSON);
    }

    @Test
    public void shouldRetrieveAPayment_fromPublicApiEndpoint() {

        String accountExternalId = testGatewayAccount.getExternalId();

        MandateFixture mandateFixture = aMandateFixture()
                .withGatewayAccountFixture(testGatewayAccount)
                .insert(testContext.getJdbi());

        PaymentFixture paymentFixture = createTransactionFixtureWith(mandateFixture, PaymentState.CREATED, "state details");

        String requestPath = CHARGE_API_PATH
                .replace("{accountId}", accountExternalId)
                .replace("{paymentExternalId}", paymentFixture.getExternalId());

        ValidatableResponse getChargeResponse = givenSetup()
                .get(requestPath)
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_AMOUNT_KEY, isNumber(paymentFixture.getAmount()))
                .body(JSON_REFERENCE_KEY, is(paymentFixture.getReference()))
                .body(JSON_DESCRIPTION_KEY, is(paymentFixture.getDescription()))
                .body(JSON_STATE_STATUS_KEY, is(paymentFixture.getState().toExternal().getStatus()))
                .body(JSON_STATE_DETAILS_KEY, is(paymentFixture.getStateDetails()));
    }
    
    @Test
    public void shouldTriggerAnInvalidMandateStateException() throws Exception {
        String accountExternalId = testGatewayAccount.getExternalId();
        PayerFixture payerFixture = aPayerFixture();
        MandateFixture mandateFixture = aMandateFixture()
                .withGatewayAccountFixture(testGatewayAccount)
                .withPayerFixture(payerFixture)
                .withState(MandateState.USER_SETUP_CANCELLED)
                .insert(testContext.getJdbi());

        String postBody = new ObjectMapper().writeValueAsString(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, "Test description")
                .put(JSON_DESCRIPTION_KEY, "Test description")
                .put(JSON_GATEWAY_ACC_KEY, accountExternalId)
                .put(JSON_MANDATE_ID_KEY, mandateFixture.getExternalId().toString())
                .build());

        String requestPath = "/v1/api/accounts/{accountId}/charges/collect"
                .replace("{accountId}", accountExternalId);

        ValidatableResponse response = givenSetup()
                .body(postBody)
                .post(requestPath)
                .then()
                .body("error_identifier", is("MANDATE_STATE_INVALID"))
                .statusCode(500);

    }
    
    @Test
    public void shouldReceiveCorrectError_whenMandateDoesNotExist() throws Exception {
        String accountExternalId = testGatewayAccount.getExternalId();
        
        String postBody = new ObjectMapper().writeValueAsString(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, "Test description")
                .put(JSON_DESCRIPTION_KEY, "Test description")
                .put(JSON_GATEWAY_ACC_KEY, accountExternalId)
                .put(JSON_MANDATE_ID_KEY, "FAKEMANDATE")
                .build());

        String requestPath = "/v1/api/accounts/{accountId}/charges/collect"
                .replace("{accountId}", accountExternalId);

        ValidatableResponse response = givenSetup()
                .body(postBody)
                .post(requestPath)
                .then()
                .body("error_identifier", is("MANDATE_ID_INVALID"))
                .statusCode(404);
    }
    
    private PaymentFixture createTransactionFixtureWith(MandateFixture mandateFixture, PaymentState paymentState,
                                                        String paymentStateDetails) {
        return aPaymentFixture()
                .withMandateFixture(mandateFixture)
                .withState(paymentState)
                .withStateDetails(paymentStateDetails)
                .insert(testContext.getJdbi());
    }

    private String expectedTransactionLocationFor(String accountId, String chargeId) {
        return "http://localhost:" + testContext.getPort() + CHARGE_API_PATH
                .replace("{accountId}", accountId)
                .replace("{paymentExternalId}", chargeId);
    }

    private RequestSpecification givenSetup() {
        return given().port(testContext.getPort())
                .contentType(JSON);
    }
}
