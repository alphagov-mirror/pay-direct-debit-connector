package uk.gov.pay.directdebit.payments.fixtures;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.jdbi.v3.core.Jdbi;
import uk.gov.pay.directdebit.common.fixtures.DbFixture;
import uk.gov.pay.directdebit.common.util.RandomIdGenerator;
import uk.gov.pay.directdebit.gatewayaccounts.model.PaymentProvider;
import uk.gov.pay.directdebit.payments.model.PaymentState;
import uk.gov.pay.directdebit.payments.model.Transaction;

public class TransactionFixture implements DbFixture<TransactionFixture, Transaction> {

    private Long id = RandomUtils.nextLong(1, 99999);

    private Long paymentRequestId = RandomUtils.nextLong(1, 99999);

    private String paymentRequestExternalId = RandomIdGenerator.newId();

    private String paymentRequestDescription = RandomStringUtils.randomAlphabetic(20);

    private String paymentRequestReference = RandomStringUtils.randomAlphabetic(20);

    private Long gatewayAccountId = RandomUtils.nextLong(1, 99999);

    private String gatewayAccountExternalId = RandomIdGenerator.newId();

    private String paymentRequestReturnUrl = "http://www." + RandomStringUtils.randomAlphabetic(10) + ".test";

    private PaymentProvider paymentProvider = PaymentProvider.SANDBOX;

    private Long amount = RandomUtils.nextLong(1, 99999);

    private Transaction.Type type = Transaction.Type.CHARGE;

    private PaymentState state = PaymentState.NEW;

    private TransactionFixture() {
    }

    public static TransactionFixture aTransactionFixture() {
        return new TransactionFixture();
    }

    public TransactionFixture withPaymentRequestId(Long paymentRequestId) {
        this.paymentRequestId = paymentRequestId;
        return this;
    }

    public TransactionFixture withPaymentRequestExternalId(String paymentRequestExternalId) {
        this.paymentRequestExternalId = paymentRequestExternalId;
        return this;
    }

    public TransactionFixture withPaymentRequestDescription(String paymentRequestDescription) {
        this.paymentRequestDescription = paymentRequestDescription;
        return this;
    }

    public TransactionFixture withPaymentRequestReference(String paymentRequestReference) {
        this.paymentRequestReference = paymentRequestReference;
        return this;
    }

    public TransactionFixture withGatewayAccountId(Long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
        return this;
    }

    public TransactionFixture withGatewayAccountExternalId(String gatewayAccountExternalId) {
        this.gatewayAccountExternalId = gatewayAccountExternalId;
        return this;
    }

    public TransactionFixture withPaymentRequestReturnUrl(String paymentRequestReturnUrl) {
        this.paymentRequestReturnUrl = paymentRequestReturnUrl;
        return this;
    }

    public TransactionFixture withPaymentProvider(PaymentProvider paymentProvider) {
        this.paymentProvider = paymentProvider;
        return this;
    }

    public TransactionFixture withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public TransactionFixture withType(Transaction.Type type) {
        this.type = type;
        return this;
    }

    public TransactionFixture withState(PaymentState state) {
        this.state = state;
        return this;
    }

    public PaymentProvider getPaymentProvider() {
        return paymentProvider;
    }

    public Long getPaymentRequestId() {
        return paymentRequestId;
    }

    public String getPaymentRequestExternalId() {
        return paymentRequestExternalId;
    }

    public Long getAmount() {
        return amount;
    }

    public Transaction.Type getType() {
        return type;
    }

    public Long getId() {
        return id;
    }

    public PaymentState getState() {
        return state;
    }

    public String getPaymentRequestReference() {
        return paymentRequestReference;
    }

    public String getPaymentRequestReturnUrl() {
        return paymentRequestReturnUrl;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public String getGatewayAccountExternalId() {
        return gatewayAccountExternalId;
    }

    public String getPaymentRequestDescription() {
        return paymentRequestDescription;
    }

    @Override
    public TransactionFixture insert(Jdbi jdbi) {
        jdbi.withHandle(h ->
                h.execute(
                        "INSERT INTO" +
                                "    transactions(\n" +
                                "        id,\n" +
                                "        payment_request_id,\n" +
                                "        amount,\n" +
                                "        type,\n" +
                                "        state\n" +
                                "    )\n" +
                                "   VALUES(?, ?, ?, ?, ?)\n",
                        id,
                        paymentRequestId,
                        amount,
                        type,
                        state
                )
        );
        return this;
    }

    @Override
    public Transaction toEntity() {
        return new Transaction(id, paymentRequestId, paymentRequestExternalId, paymentRequestDescription, paymentRequestReference, gatewayAccountId, gatewayAccountExternalId, paymentProvider, paymentRequestReturnUrl, amount, type, state);
    }

}
