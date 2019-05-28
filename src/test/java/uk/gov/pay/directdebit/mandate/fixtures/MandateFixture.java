package uk.gov.pay.directdebit.mandate.fixtures;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.jdbi.v3.core.Jdbi;
import uk.gov.pay.directdebit.common.fixtures.DbFixture;
import uk.gov.pay.directdebit.common.util.RandomIdGenerator;
import uk.gov.pay.directdebit.mandate.model.Mandate;
import uk.gov.pay.directdebit.mandate.model.MandateBankStatementReference;
import uk.gov.pay.directdebit.mandate.model.MandateState;
import uk.gov.pay.directdebit.mandate.model.MandateType;
import uk.gov.pay.directdebit.mandate.model.subtype.MandateExternalId;
import uk.gov.pay.directdebit.payers.fixtures.PayerFixture;
import uk.gov.pay.directdebit.payers.model.Payer;
import uk.gov.pay.directdebit.payments.fixtures.GatewayAccountFixture;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class MandateFixture implements DbFixture<MandateFixture, Mandate> {

    private Long id = RandomUtils.nextLong(1, 99999);
    private MandateExternalId mandateExternalId = MandateExternalId.valueOf(RandomIdGenerator.newId());
    private MandateBankStatementReference mandateReference = MandateBankStatementReference.valueOf(RandomStringUtils.randomAlphanumeric(18));
    private String serviceReference = RandomStringUtils.randomAlphanumeric(18);
    private MandateState state = MandateState.CREATED;
    private String returnUrl = "http://service.test/success-page";
    private MandateType mandateType = MandateType.ON_DEMAND;
    private GatewayAccountFixture gatewayAccountFixture = GatewayAccountFixture.aGatewayAccountFixture();
    private PayerFixture payerFixture = null;
    private ZonedDateTime createdDate = ZonedDateTime.now(ZoneOffset.UTC);

    private MandateFixture() {
    }

    public static MandateFixture aMandateFixture() {
        return new MandateFixture();
    }

    public MandateFixture withGatewayAccountFixture(GatewayAccountFixture gatewayAccountFixture) {
        this.gatewayAccountFixture = gatewayAccountFixture;
        return this;
    }

    public MandateFixture withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public MandateFixture withPayerFixture(
            PayerFixture payerFixture) {
        this.payerFixture = payerFixture;
        return this;
    }

    public Long getId() {
        return id;
    }

    public MandateFixture setId(Long id) {
        this.id = id;
        return this;
    }

    public MandateType getMandateType() {
        return mandateType;
    }

    public MandateFixture withMandateType(MandateType mandateType) {
        this.mandateType = mandateType;
        return this;
    }

    public MandateExternalId getExternalId() {
        return mandateExternalId;
    }

    public MandateFixture withExternalId(MandateExternalId externalId) {
        this.mandateExternalId = externalId;
        return this;
    }

    public MandateBankStatementReference getMandateReference() {
        return mandateReference;
    }

    public MandateFixture withMandateReference(MandateBankStatementReference mandateReference) {
        this.mandateReference = mandateReference;
        return this;
    }

    public String getServiceReference() {
        return serviceReference;
    }

    public MandateFixture withServiceReference(String serviceReference) {
        this.serviceReference = serviceReference;
        return this;
    }

    public MandateState getState() {
        return state;
    }

    public MandateFixture withState(MandateState state) {
        this.state = state;
        return this;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public MandateFixture withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }

    public MandateFixture withId(Long id) {
        this.id = id;
        return this;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public MandateFixture insert(Jdbi jdbi) {
        jdbi.withHandle(h ->
                h.execute(
                        "INSERT INTO mandates (\n" +
                                "  id,\n" +
                                "  gateway_account_id,\n" +
                                "  external_id,\n" +
                                "  type,\n" +
                                "  mandate_reference,\n" +
                                "  service_reference,\n" +
                                "  return_url,\n" +
                                "  state,\n" +
                                "  created_date\n" +
                                ") VALUES (\n" +
                                "  ?, ?, ?, ?, ?, ?, ?, ?, ?\n" +
                                ")\n",
                        id,
                        gatewayAccountFixture.getId(),
                        mandateExternalId.toString(),
                        mandateType.toString(),
                        mandateReference.toString(),
                        serviceReference,
                        returnUrl,
                        state.toString(),
                        createdDate
                )
        );
        if (payerFixture != null) {
            payerFixture.withMandateId(id);
            payerFixture.insert(jdbi);
        }
        return this;
    }

    @Override
    public Mandate toEntity() {
        Payer payer = payerFixture != null ? payerFixture.toEntity() : null;
        return new Mandate(
                id,
                gatewayAccountFixture.toEntity(),
                mandateType,
                mandateExternalId,
                mandateReference,
                serviceReference,
                state,
                returnUrl,
                createdDate,
                payer
        );
    }
}
