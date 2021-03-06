package uk.gov.pay.directdebit.mandate.dao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.directdebit.DirectDebitConnectorApp;
import uk.gov.pay.directdebit.common.util.RandomIdGenerator;
import uk.gov.pay.directdebit.gatewayaccounts.model.GoCardlessOrganisationId;
import uk.gov.pay.directdebit.junit.DropwizardConfig;
import uk.gov.pay.directdebit.junit.DropwizardJUnitRunner;
import uk.gov.pay.directdebit.junit.DropwizardTestContext;
import uk.gov.pay.directdebit.junit.TestContext;
import uk.gov.pay.directdebit.mandate.fixtures.MandateFixture;
import uk.gov.pay.directdebit.mandate.model.GoCardlessMandateId;
import uk.gov.pay.directdebit.mandate.model.Mandate;
import uk.gov.pay.directdebit.mandate.model.MandateBankStatementReference;
import uk.gov.pay.directdebit.mandate.model.MandateState;
import uk.gov.pay.directdebit.mandate.model.PaymentProviderMandateId;
import uk.gov.pay.directdebit.mandate.model.SandboxMandateId;
import uk.gov.pay.directdebit.mandate.model.subtype.MandateExternalId;
import uk.gov.pay.directdebit.payments.fixtures.GatewayAccountFixture;
import uk.gov.pay.directdebit.tokens.fixtures.TokenFixture;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.time.ZonedDateTime.now;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.directdebit.gatewayaccounts.model.PaymentProvider.GOCARDLESS;
import static uk.gov.pay.directdebit.gatewayaccounts.model.PaymentProvider.SANDBOX;
import static uk.gov.pay.directdebit.mandate.model.Mandate.MandateBuilder.aMandate;
import static uk.gov.pay.directdebit.mandate.model.MandateState.ACTIVE;
import static uk.gov.pay.directdebit.mandate.model.MandateState.AWAITING_DIRECT_DEBIT_DETAILS;
import static uk.gov.pay.directdebit.mandate.model.MandateState.CREATED;
import static uk.gov.pay.directdebit.mandate.model.MandateState.SUBMITTED_TO_PROVIDER;
import static uk.gov.pay.directdebit.tokens.fixtures.TokenFixture.aTokenFixture;
import static uk.gov.pay.directdebit.util.ZonedDateTimeTimestampMatcher.isDate;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = DirectDebitConnectorApp.class, config = "config/test-it-config.yaml")
public class MandateDaoIT {

    @DropwizardTestContext
    private TestContext testContext;

    private MandateDao mandateDao;
    private GatewayAccountFixture gatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture();

    @Before
    public void setUp() {
        gatewayAccountFixture.insert(testContext.getJdbi());
        mandateDao = testContext.getJdbi().onDemand(MandateDao.class);
    }

    @Test
    public void shouldInsertAMandateWithServiceReference() {
        ZonedDateTime createdDate = now();
        Long id = mandateDao.insert(aMandate()
                        .withGatewayAccount(gatewayAccountFixture.toEntity())
                        .withExternalId(MandateExternalId.valueOf(RandomIdGenerator.newId()))
                        .withMandateBankStatementReference(MandateBankStatementReference.valueOf("test-reference"))
                        .withDescription("a description")
                        .withServiceReference("test-service-reference")
                        .withState(ACTIVE)
                        .withReturnUrl("https://www.example.com/return_url")
                        .withCreatedDate(createdDate)
                        .build());

        Map<String, Object> mandate = testContext.getDatabaseTestHelper().getMandateById(id);
        assertThat(mandate.get("id"), is(id));
        assertThat(mandate.get("external_id"), is(notNullValue()));
        assertThat(mandate.get("mandate_reference"), is("test-reference"));
        assertThat(mandate.get("service_reference"), is("test-service-reference"));
        assertThat(mandate.get("description"), is("a description"));
        assertThat(mandate.get("return_url"), is("https://www.example.com/return_url"));
        assertThat(mandate.get("state"), is("ACTIVE"));
        assertThat((Timestamp) mandate.get("created_date"), isDate(createdDate));
        assertThat(mandate.get("payment_provider"), is(nullValue()));
    }

    @Test
    public void shouldFindAMandateById() {
        PaymentProviderMandateId paymentProviderMandateId = SandboxMandateId.valueOf("aSandboxMandateId");
        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withMandateBankStatementReference(MandateBankStatementReference.valueOf("test-reference"))
                .withServiceReference("test-service-reference")
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withPaymentProviderId(paymentProviderMandateId)
                .insert(testContext.getJdbi());

        Mandate mandate = mandateDao.findById(mandateFixture.getId()).get();
        assertThat(mandate.getId(), is(mandateFixture.getId()));
        assertThat(mandate.getExternalId(), is(notNullValue()));
        assertThat(mandate.getMandateBankStatementReference().get(), is(MandateBankStatementReference.valueOf("test-reference")));
        assertThat(mandate.getServiceReference(), is("test-service-reference"));
        assertThat(mandate.getState(), is(CREATED));
        assertThat(mandate.getPaymentProviderMandateId().get(), is(paymentProviderMandateId));
    }

    @Test
    public void shouldNotFindAMandateById_ifIdIsInvalid() {
        Long invalidId = 29L;
        assertThat(mandateDao.findById(invalidId), is(Optional.empty()));
    }

    @Test
    public void shouldFindAMandateByTokenId() {
        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withMandateBankStatementReference(MandateBankStatementReference.valueOf("test-reference"))
                .withServiceReference("test-service-reference")
                .withGatewayAccountFixture(gatewayAccountFixture)
                .insert(testContext.getJdbi());

        TokenFixture token = aTokenFixture()
                .withMandateId(mandateFixture.getId())
                .insert(testContext.getJdbi());

        Mandate mandate = mandateDao.findByTokenId(token.getToken()).get();
        assertThat(mandate.getId(), is(mandateFixture.getId()));
        assertThat(mandate.getExternalId(), is(mandateFixture.getExternalId()));
        assertThat(mandate.getMandateBankStatementReference().get(), is(MandateBankStatementReference.valueOf("test-reference")));
        assertThat(mandate.getServiceReference(), is("test-service-reference"));
        assertThat(mandate.getState(), is(CREATED));
    }

    @Test
    public void shouldNotFindATransactionByTokenId_ifTokenIdIsInvalid() {
        String tokenId = "non_existing_tokenId";
        assertThat(mandateDao.findByTokenId(tokenId), is(Optional.empty()));
    }

    @Test
    public void shouldFindAMandateByExternalId() {
        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withMandateBankStatementReference(MandateBankStatementReference.valueOf("test-reference"))
                .withServiceReference("test-service-reference")
                .withGatewayAccountFixture(gatewayAccountFixture)
                .insert(testContext.getJdbi());

        Mandate mandate = mandateDao.findByExternalId(mandateFixture.getExternalId()).get();
        assertThat(mandate.getId(), is(mandateFixture.getId()));
        assertThat(mandate.getExternalId(), is(notNullValue()));
        assertThat(mandate.getMandateBankStatementReference().get(), is(MandateBankStatementReference.valueOf("test-reference")));
        assertThat(mandate.getServiceReference(), is("test-service-reference"));
        assertThat(mandate.getState(), is(CREATED));
    }

    @Test
    public void shouldNotFindAMandateByExternalId_ifExternalIdDoesNotExist() {
        MandateExternalId invalidMandateId = MandateExternalId.valueOf("invalid1d");
        assertThat(mandateDao.findByExternalId(invalidMandateId), is(Optional.empty()));
    }

    @Test
    public void shouldFindAMandateByExternalIdAndGatewayAccount() {
        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withMandateBankStatementReference(MandateBankStatementReference.valueOf("test-reference"))
                .withServiceReference("test-service-reference")
                .withGatewayAccountFixture(gatewayAccountFixture)
                .insert(testContext.getJdbi());

        Mandate mandate = mandateDao.findByExternalIdAndGatewayAccountExternalId(mandateFixture.getExternalId(), gatewayAccountFixture.getExternalId()).get();

        assertThat(mandate.getId(), is(mandateFixture.getId()));
        assertThat(mandate.getExternalId(), is(notNullValue()));
        assertThat(mandate.getMandateBankStatementReference().get(), is(MandateBankStatementReference.valueOf("test-reference")));
        assertThat(mandate.getServiceReference(), is("test-service-reference"));
        assertThat(mandate.getState(), is(CREATED));
    }

    @Test
    public void shouldNotFindAMandateByExternalIdAndGatewayAccountId_ifGatewayAccountIdIsNotCorrect() {
        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withMandateBankStatementReference(MandateBankStatementReference.valueOf("test-reference"))
                .withServiceReference("test-service-reference")
                .withGatewayAccountFixture(gatewayAccountFixture)
                .insert(testContext.getJdbi());

        assertThat(mandateDao.findByExternalIdAndGatewayAccountExternalId(mandateFixture.getExternalId(), "xxxx"), is(Optional.empty()));
    }

    @Test
    public void shouldFindAMandateByPaymentProviderIdAndOrganisationId() {
        MandateExternalId mandateExternalId = MandateExternalId.valueOf("expectedExternalId");
        GoCardlessMandateId goCardlessMandateId = GoCardlessMandateId.valueOf("expectedGoCardlessMandateId");
        GoCardlessOrganisationId goCardlessOrganisationId = GoCardlessOrganisationId.valueOf("expectedGoCardlessOrganisationId");

        GatewayAccountFixture gatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(GOCARDLESS)
                .withOrganisation(goCardlessOrganisationId)
                .insert(testContext.getJdbi());

        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withExternalId(mandateExternalId)
                .withPaymentProviderId(goCardlessMandateId)
                .insert(testContext.getJdbi());

        Mandate mandate = mandateDao.findByPaymentProviderMandateIdAndOrganisation(GOCARDLESS, goCardlessMandateId, goCardlessOrganisationId).get();

        assertThat(mandate.getId(), is(mandateFixture.getId()));
        assertThat(mandate.getExternalId().toString(), is("expectedExternalId"));
        assertThat(mandate.getPaymentProviderMandateId().get().toString(), is("expectedGoCardlessMandateId"));
        assertThat(mandate.getState(), is(CREATED));
    }

    @Test
    public void shouldNotFindAMandateByPaymentProviderIdIfOrganisationIdDoesNotMatch() {
        MandateExternalId mandateExternalId = MandateExternalId.valueOf("expectedExternalId");
        GoCardlessMandateId goCardlessMandateId = GoCardlessMandateId.valueOf("expectedGoCardlessMandateId");
        GoCardlessOrganisationId goCardlessOrganisationId = GoCardlessOrganisationId.valueOf("expectedGoCardlessOrganisationId");

        GatewayAccountFixture gatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(GOCARDLESS)
                .withOrganisation(goCardlessOrganisationId)
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withExternalId(mandateExternalId)
                .withPaymentProviderId(goCardlessMandateId)
                .insert(testContext.getJdbi());

        Optional<Mandate> mandate = mandateDao.findByPaymentProviderMandateIdAndOrganisation(GOCARDLESS, goCardlessMandateId,
                GoCardlessOrganisationId.valueOf("differentOrganisationId"));

        assertThat(mandate, is(Optional.empty()));
    }

    @Test
    public void shouldNotFindAMandateByPaymentProviderIdIfPaymentProviderDoesNotMatch() {
        MandateExternalId mandateExternalId = MandateExternalId.valueOf("expectedExternalId");
        GoCardlessMandateId goCardlessMandateId = GoCardlessMandateId.valueOf("expectedGoCardlessMandateId");
        GoCardlessOrganisationId goCardlessOrganisationId = GoCardlessOrganisationId.valueOf("expectedGoCardlessOrganisationId");

        GatewayAccountFixture gatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(GOCARDLESS)
                .withOrganisation(goCardlessOrganisationId)
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withExternalId(mandateExternalId)
                .withPaymentProviderId(goCardlessMandateId)
                .insert(testContext.getJdbi());

        Optional<Mandate> mandate = mandateDao.findByPaymentProviderMandateIdAndOrganisation(SANDBOX, GoCardlessMandateId.valueOf("differentMandateId"),
                GoCardlessOrganisationId.valueOf("expectedGoCardlessOrganisationId"));

        assertThat(mandate, is(Optional.empty()));
    }

    @Test
    public void shouldFindAMandateByPaymentProviderIdAndNoOrganisationId() {
        var mandateExternalId = MandateExternalId.valueOf("expectedExternalId");
        var sandboxMandateId = SandboxMandateId.valueOf("expectedSandboxMandateId");

        GatewayAccountFixture gatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(SANDBOX)
                .withOrganisation(null)
                .insert(testContext.getJdbi());

        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withExternalId(mandateExternalId)
                .withPaymentProviderId(sandboxMandateId)
                .insert(testContext.getJdbi());

        Mandate mandate = mandateDao.findByPaymentProviderMandateId(SANDBOX, sandboxMandateId).get();

        assertThat(mandate.getId(), is(mandateFixture.getId()));
        assertThat(mandate.getExternalId().toString(), is("expectedExternalId"));
        assertThat(mandate.getPaymentProviderMandateId().get().toString(), is("expectedSandboxMandateId"));
    }
    
    @Test
    public void shouldNotFindAMandateByPaymentProviderIdAndNoOrganisationIfPaymentProviderIdDoesNotMatch() {
        var mandateExternalId = MandateExternalId.valueOf("expectedExternalId");
        var sandboxMandateId = SandboxMandateId.valueOf("expectedSandboxMandateId");

        GatewayAccountFixture gatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(SANDBOX)
                .withOrganisation(null)
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withExternalId(mandateExternalId)
                .withPaymentProviderId(sandboxMandateId)
                .insert(testContext.getJdbi());

        Optional<Mandate> mandate = mandateDao.findByPaymentProviderMandateId(SANDBOX, SandboxMandateId.valueOf("differentMandateId"));

        assertThat(mandate, is(Optional.empty()));
    }

    @Test
    public void shouldNotFindAMandateByPaymentProviderIdAndNoOrganisationIfProviderDoesNotMatch() {
        var mandateExternalId = MandateExternalId.valueOf("expectedExternalId");
        var goCardlessMandateId = GoCardlessMandateId.valueOf("expectedSandboxMandateId");

        GatewayAccountFixture gatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(SANDBOX)
                .withOrganisation(null)
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withExternalId(mandateExternalId)
                .withPaymentProviderId(goCardlessMandateId)
                .insert(testContext.getJdbi());

        Optional<Mandate> mandate = mandateDao.findByPaymentProviderMandateId(GOCARDLESS, goCardlessMandateId);

        assertThat(mandate, is(Optional.empty()));
    }
    
    @Test
    public void shouldNotFindAMandateByPaymentProviderIdAndNoOrganisationIdIfOrganisationIsNotNull() {
        var mandateExternalId = MandateExternalId.valueOf("expectedExternalId");
        var goCardlessMandateId = GoCardlessMandateId.valueOf("expectedSandboxMandateId");

        GatewayAccountFixture gatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(GOCARDLESS)
                .withOrganisation(GoCardlessOrganisationId.valueOf("organisationId"))
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withExternalId(mandateExternalId)
                .withPaymentProviderId(goCardlessMandateId)
                .insert(testContext.getJdbi());

        Optional<Mandate> mandate = mandateDao.findByPaymentProviderMandateId(GOCARDLESS, goCardlessMandateId);

        assertThat(mandate, is(Optional.empty()));
    }

    @Test
    public void shouldUpdateStateAndReturnNumberOfAffectedRows() {
        Mandate testMandate = MandateFixture.aMandateFixture().withGatewayAccountFixture(gatewayAccountFixture).insert(testContext.getJdbi()).toEntity();
        MandateState newState = MandateState.FAILED;
        int numOfUpdatedMandates = mandateDao.updateState(testMandate.getId(), newState);

        Map<String, Object> mandateAfterUpdate = testContext.getDatabaseTestHelper().getMandateById(testMandate.getId());
        assertThat(numOfUpdatedMandates, is(1));
        assertThat(mandateAfterUpdate.get("id"), is(testMandate.getId()));
        assertThat(mandateAfterUpdate.get("external_id"), is(testMandate.getExternalId().toString()));
        assertThat(mandateAfterUpdate.get("mandate_reference"), is(testMandate.getMandateBankStatementReference().get().toString()));
        assertThat(mandateAfterUpdate.get("service_reference"), is(testMandate.getServiceReference()));
        assertThat(mandateAfterUpdate.get("state"), is(newState.toString()));
    }

    @Test
    public void shouldNotUpdateAnythingIfTransactionDoesNotExist() {
        int numOfUpdatedMandates = mandateDao.updateState(34L, MandateState.FAILED);
        assertThat(numOfUpdatedMandates, is(0));
    }

    @Test
    public void shouldUpdateStateWithDetailsAndReturnNumberOfAffectedRows() {
        GatewayAccountFixture goCardlessGatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(GOCARDLESS)
                .withOrganisation(GoCardlessOrganisationId.valueOf("Organisation ID we want"))
                .insert(testContext.getJdbi());

        GatewayAccountFixture goCardlessGatewayAccountFixtureWithWrongOrganisation = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(GOCARDLESS)
                .withOrganisation(GoCardlessOrganisationId.valueOf("Different organisation"))
                .insert(testContext.getJdbi());

        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(goCardlessGatewayAccountFixture)
                .withExternalId(MandateExternalId.valueOf("Mandate we want"))
                .withPaymentProviderId(GoCardlessMandateId.valueOf("Mandate ID we want"))
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(goCardlessGatewayAccountFixture)
                .withPaymentProviderId(GoCardlessMandateId.valueOf("Different mandate ID"))
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(goCardlessGatewayAccountFixtureWithWrongOrganisation)
                .withPaymentProviderId(GoCardlessMandateId.valueOf("Mandate ID we want"))
                .insert(testContext.getJdbi());

        int numOfUpdatedMandates = mandateDao.updateStateAndDetails(mandateFixture.getId(), SUBMITTED_TO_PROVIDER,
                "state details","state details description");

        assertThat(numOfUpdatedMandates, is(1));

        Mandate mandate = mandateDao.findByExternalId(MandateExternalId.valueOf("Mandate we want")).get();
        assertThat(mandate.getState(), is(SUBMITTED_TO_PROVIDER));
        assertThat(mandate.getStateDetails(), is(Optional.of("state details")));
        assertThat(mandate.getStateDetailsDescription(), is(Optional.of("state details description")));
    }

    @Test
    public void shouldUpdateStateWithNoDetailsAndDescriptionAndReturnNumberOfAffectedRows() {
        GatewayAccountFixture goCardlessGatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(GOCARDLESS)
                .withOrganisation(GoCardlessOrganisationId.valueOf("Organisation ID we want"))
                .insert(testContext.getJdbi());

        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(goCardlessGatewayAccountFixture)
                .withExternalId(MandateExternalId.valueOf("Mandate we want"))
                .withPaymentProviderId(GoCardlessMandateId.valueOf("Mandate ID we want"))
                .withStateDetails("state details before update")
                .withStateDetailsDescription("state details description before update")
                .insert(testContext.getJdbi());

        int numOfUpdatedMandates = mandateDao.updateStateAndDetails(mandateFixture.getId(), SUBMITTED_TO_PROVIDER,
                null, null);

        assertThat(numOfUpdatedMandates, is(1));

        Mandate mandate = mandateDao.findByExternalId(MandateExternalId.valueOf("Mandate we want")).get();
        assertThat(mandate.getState(), is(SUBMITTED_TO_PROVIDER));
        assertThat(mandate.getStateDetails(), is(Optional.empty()));
        assertThat(mandate.getStateDetails(), is(Optional.empty()));
    }

    @Test
    public void shouldUpdateStateAndDetailsAndReturnNumberOfAffectedRows() {
        GatewayAccountFixture gatewayAccountFixtureWithNoOrganisation = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(SANDBOX)
                .withOrganisation(null)
                .insert(testContext.getJdbi());

        GatewayAccountFixture gatewayAccountFixtureWithWrongOrganisation = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(SANDBOX)
                .withOrganisation(GoCardlessOrganisationId.valueOf("Different organisation"))
                .insert(testContext.getJdbi());

        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixtureWithNoOrganisation)
                .withExternalId(MandateExternalId.valueOf("Mandate we want"))
                .withPaymentProviderId(SandboxMandateId.valueOf("Mandate ID we want"))
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixtureWithNoOrganisation)
                .withPaymentProviderId(SandboxMandateId.valueOf("Different mandate ID"))
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixtureWithWrongOrganisation)
                .withPaymentProviderId(SandboxMandateId.valueOf("Mandate ID we want"))
                .insert(testContext.getJdbi());

        int numOfUpdatedMandates = mandateDao.updateStateAndDetails(
                mandateFixture.getId(),
                SUBMITTED_TO_PROVIDER,
                "state details",
                "state details description");

        assertThat(numOfUpdatedMandates, is(1));

        Mandate mandate = mandateDao.findByExternalId(MandateExternalId.valueOf("Mandate we want")).get();
        assertThat(mandate.getState(), is(SUBMITTED_TO_PROVIDER));
        assertThat(mandate.getStateDetails(), is(Optional.of("state details")));
        assertThat(mandate.getStateDetailsDescription(), is(Optional.of("state details description")));
    }

    @Test
    public void shouldUpdateStateWithNoDetailsOrDescriptionAndReturnNumberOfAffectedRows() {
        GatewayAccountFixture gatewayAccountFixtureWithNoOrganisation = GatewayAccountFixture.aGatewayAccountFixture()
                .withPaymentProvider(SANDBOX)
                .withOrganisation(null)
                .insert(testContext.getJdbi());

        MandateFixture mandateFixture = MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixtureWithNoOrganisation)
                .withExternalId(MandateExternalId.valueOf("Mandate we want"))
                .withPaymentProviderId(SandboxMandateId.valueOf("Mandate ID we want"))
                .withStateDetails("state details before update")
                .withStateDetailsDescription("state details description before update")
                .insert(testContext.getJdbi());

        int numOfUpdatedMandates = mandateDao.updateStateAndDetails(
                mandateFixture.getId(), SUBMITTED_TO_PROVIDER, null, null);

        assertThat(numOfUpdatedMandates, is(1));

        Mandate mandate = mandateDao.findByExternalId(MandateExternalId.valueOf("Mandate we want")).get();
        assertThat(mandate.getState(), is(SUBMITTED_TO_PROVIDER));
        assertThat(mandate.getStateDetails(), is(Optional.empty()));
        assertThat(mandate.getStateDetailsDescription(), is(Optional.empty()));
    }

    @Test
    public void shouldUpdateReferenceAndPaymentProviderId() {
        var bankStatementReference = MandateBankStatementReference.valueOf("newReference");
        var paymentProviderId = GoCardlessMandateId.valueOf("aPaymentProviderId");

        Mandate testMandate = MandateFixture.aMandateFixture()
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withMandateBankStatementReference(MandateBankStatementReference.valueOf("old-reference"))
                .insert(testContext.getJdbi())
                .toEntity();

        Mandate confirmedTestMandate = Mandate.MandateBuilder.fromMandate(testMandate)
                .withMandateBankStatementReference(bankStatementReference)
                .withPaymentProviderId(paymentProviderId)
                .build();

        int numOfUpdatedMandates = mandateDao.updateReferenceAndPaymentProviderId(confirmedTestMandate);

        Map<String, Object> mandateAfterUpdate = testContext.getDatabaseTestHelper().getMandateById(testMandate.getId());
        assertThat(numOfUpdatedMandates, is(1));
        assertThat(mandateAfterUpdate.get("id"), is(testMandate.getId()));
        assertThat(mandateAfterUpdate.get("external_id"), is(testMandate.getExternalId().toString()));
        assertThat(mandateAfterUpdate.get("mandate_reference"), is(bankStatementReference.toString()));
        assertThat(mandateAfterUpdate.get("state"), is(testMandate.getState().toString()));
        assertThat(mandateAfterUpdate.get("payment_provider_id"), is(paymentProviderId.toString()));
    }

    @Test
    public void shouldNotFindMandateInWrongState() {
        MandateFixture.aMandateFixture()
                .withState(SUBMITTED_TO_PROVIDER)
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withCreatedDate(now().minusMinutes(91L))
                .insert(testContext.getJdbi());
        
        Set<MandateState> states = Set.of(CREATED, AWAITING_DIRECT_DEBIT_DETAILS);
        List<Mandate> transactions = mandateDao.findAllMandatesBySetOfStatesAndMaxCreationTime(states, now().minusMinutes(90L));
        assertThat(transactions.size(), is(0));
    }

    @Test
    public void shouldNotFindMandateWrongCreationTime() {
        MandateFixture.aMandateFixture()
                .withState(CREATED)
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withCreatedDate(now())
                .insert(testContext.getJdbi());

        Set<MandateState> states = Set.of(CREATED, AWAITING_DIRECT_DEBIT_DETAILS);
        List<Mandate> transactions = mandateDao.findAllMandatesBySetOfStatesAndMaxCreationTime(states, now().minusMinutes(90L));
        assertThat(transactions.size(), is(0));
    }

    @Test
    public void shouldFindThreeMandates() {
        MandateFixture.aMandateFixture()
                .withState(AWAITING_DIRECT_DEBIT_DETAILS)
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withCreatedDate(now().minusMinutes(200L))
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withState(CREATED)
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withCreatedDate(now().minusMinutes(100L))
                .insert(testContext.getJdbi());

        MandateFixture.aMandateFixture()
                .withState(SUBMITTED_TO_PROVIDER)
                .withGatewayAccountFixture(gatewayAccountFixture)
                .withCreatedDate(now().minusMinutes(91L))
                .insert(testContext.getJdbi());

        Set<MandateState> states = Set.of(CREATED, AWAITING_DIRECT_DEBIT_DETAILS, SUBMITTED_TO_PROVIDER);
        List<Mandate> transactions = mandateDao.findAllMandatesBySetOfStatesAndMaxCreationTime(states, now().minusMinutes(90L));
        assertThat(transactions.size(), is(3));
    }
}
