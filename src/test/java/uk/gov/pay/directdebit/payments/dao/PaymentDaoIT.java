package uk.gov.pay.directdebit.payments.dao;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.directdebit.DirectDebitConnectorApp;
import uk.gov.pay.directdebit.gatewayaccounts.model.PaymentProvider;
import uk.gov.pay.directdebit.junit.DropwizardConfig;
import uk.gov.pay.directdebit.junit.DropwizardJUnitRunner;
import uk.gov.pay.directdebit.junit.DropwizardTestContext;
import uk.gov.pay.directdebit.junit.TestContext;
import uk.gov.pay.directdebit.mandate.fixtures.MandateFixture;
import uk.gov.pay.directdebit.payments.fixtures.GatewayAccountFixture;
import uk.gov.pay.directdebit.payments.fixtures.PaymentFixture;
import uk.gov.pay.directdebit.payments.model.Payment;
import uk.gov.pay.directdebit.payments.model.PaymentState;
import uk.gov.pay.directdebit.payments.model.PaymentStatesGraph;
import uk.gov.pay.directdebit.payments.model.SandboxPaymentId;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.directdebit.payments.fixtures.GatewayAccountFixture.aGatewayAccountFixture;
import static uk.gov.pay.directdebit.payments.fixtures.PaymentFixture.aPaymentFixture;
import static uk.gov.pay.directdebit.util.NumberMatcher.isNumber;
import static uk.gov.pay.directdebit.util.ZonedDateTimeTimestampMatcher.isDate;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = DirectDebitConnectorApp.class, config = "config/test-it-config.yaml")
public class PaymentDaoIT {

    private static final PaymentState STATE = PaymentState.NEW;
    private static final long AMOUNT = 10L;

    @DropwizardTestContext
    private TestContext testContext;
    private PaymentDao paymentDao;

    private GatewayAccountFixture testGatewayAccount;
    private PaymentFixture testPayment;
    private MandateFixture testMandate;

    @Before
    public void setup() {
        paymentDao = testContext.getJdbi().onDemand(PaymentDao.class);
        testGatewayAccount = aGatewayAccountFixture().insert(testContext.getJdbi());
        testMandate = MandateFixture.aMandateFixture().withGatewayAccountFixture(testGatewayAccount).insert(testContext.getJdbi());
        testPayment = generateNewPaymentFixture(testMandate, STATE, AMOUNT);
    }

    @Test
    public void shouldInsertAPayment() {
        Payment payment = testPayment
                .withPaymentProviderId(SandboxPaymentId.valueOf("expectedPaymentProviderId"))
                .toEntity();
        Long id = paymentDao.insert(payment);

        Map<String, Object> foundPayment = testContext.getDatabaseTestHelper().getPaymentById(id);
        assertThat(foundPayment.get("id"), is(id));
        assertThat(foundPayment.get("mandate_id"), is(testMandate.getId()));
        assertThat((Long) foundPayment.get("amount"), isNumber(AMOUNT));
        assertThat(PaymentState.valueOf((String) foundPayment.get("state")), is(STATE));
        assertThat(foundPayment.get("payment_provider_id"), is("expectedPaymentProviderId"));
    }

    @Test
    public void shouldInsertAPaymentWithoutProviderId() {
        Payment payment = testPayment
                .withPaymentProviderId(null)
                .toEntity();
        Long id = paymentDao.insert(payment);

        Map<String, Object> foundPayment = testContext.getDatabaseTestHelper().getPaymentById(id);
        assertThat(foundPayment.get("id"), is(id));
        assertThat(foundPayment.get("mandate_id"), is(testMandate.getId()));
        assertThat((Long) foundPayment.get("amount"), isNumber(AMOUNT));
        assertThat(PaymentState.valueOf((String) foundPayment.get("state")), is(STATE));
        assertThat(foundPayment.get("payment_provider_id"), is(nullValue()));
    }

    @Test
    public void shouldGetAPaymentById() {
        SandboxPaymentId providerId = SandboxPaymentId.valueOf("expectedPaymentProviderId");
        testPayment
                .withPaymentProviderId(providerId)
                .insert(testContext.getJdbi());

        Payment payment = paymentDao.findById(testPayment.getId()).get();

        assertThat(payment.getId(), is(testPayment.getId()));
        assertThat(payment.getMandate(), is(testMandate.toEntity()));
        assertThat(payment.getExternalId(), is(testPayment.getExternalId()));
        assertThat(payment.getDescription(), is(testPayment.getDescription()));
        assertThat(payment.getReference(), is(testPayment.getReference()));
        assertThat(payment.getAmount(), is(AMOUNT));
        assertThat(payment.getState(), is(STATE));
        assertThat(payment.getCreatedDate(), is(testPayment.getCreatedDate()));
        assertThat(payment.getProviderId(), is(providerId));
    }

    @Test
    public void shouldGetAPaymentByExternalId() {
        testPayment.insert(testContext.getJdbi());
        Payment payment = paymentDao.findByExternalId(testPayment.getExternalId()).get();
        assertThat(payment.getId(), is(testPayment.getId()));
        assertThat(payment.getMandate(), is(testMandate.toEntity()));
        assertThat(payment.getExternalId(), is(testPayment.getExternalId()));
        assertThat(payment.getDescription(), is(testPayment.getDescription()));
        assertThat(payment.getReference(), is(testPayment.getReference()));
        assertThat(payment.getAmount(), is(AMOUNT));
        assertThat(payment.getState(), is(STATE));
        assertThat(payment.getCreatedDate(), is(testPayment.getCreatedDate()));
    }

    @Test
    public void shouldFindAllPaymentsByPaymentStateAndProvider() {
        GatewayAccountFixture goCardlessGatewayAccount = aGatewayAccountFixture().withPaymentProvider(PaymentProvider.GOCARDLESS).insert(testContext.getJdbi());
        GatewayAccountFixture sandboxGatewayAccount = aGatewayAccountFixture().withPaymentProvider(PaymentProvider.SANDBOX).insert(testContext.getJdbi());

        MandateFixture sandboxMandate = MandateFixture.aMandateFixture().withGatewayAccountFixture(sandboxGatewayAccount).insert(testContext.getJdbi());
        MandateFixture goCardlessMandate = MandateFixture.aMandateFixture().withGatewayAccountFixture(goCardlessGatewayAccount).insert(testContext.getJdbi());
        PaymentFixture sandboxCharge =
                generateNewPaymentFixture(sandboxMandate, PaymentState.NEW, AMOUNT);
        
        generateNewPaymentFixture(goCardlessMandate, PaymentState.NEW, AMOUNT);
        sandboxCharge.insert(testContext.getJdbi());

        PaymentFixture successSandboxCharge =
                generateNewPaymentFixture(sandboxMandate, PaymentState.SUCCESS, AMOUNT);
        successSandboxCharge.insert(testContext.getJdbi());

        PaymentFixture goCardlessSuccessCharge =
                generateNewPaymentFixture(goCardlessMandate, PaymentState.SUCCESS, AMOUNT);
        goCardlessSuccessCharge.insert(testContext.getJdbi());

        List<Payment> successPaymentsList = paymentDao.findAllByPaymentStateAndProvider(PaymentState.SUCCESS, PaymentProvider.SANDBOX);
        assertThat(successPaymentsList.size(), is(1));
        assertThat(successPaymentsList.get(0).getState(), is(PaymentState.SUCCESS));
        assertThat(successPaymentsList.get(0).getMandate().getGatewayAccount().getPaymentProvider(), is(PaymentProvider.SANDBOX));
    }

    @Test
    public void shouldNotFindAnyPaymentByPaymentState_ifPaymentStateIsNotUsed() {
        PaymentFixture processingDirectDebitPaymentStatePaymentFixture =
                generateNewPaymentFixture(testMandate, PaymentState.NEW, AMOUNT);
        processingDirectDebitPaymentStatePaymentFixture.insert(testContext.getJdbi());

        List<Payment> successPaymentsList = paymentDao.findAllByPaymentStateAndProvider(PaymentState.SUCCESS, PaymentProvider.SANDBOX);
        assertThat(successPaymentsList.size(), is(0));
    }

    @Test
    public void shouldUpdateStateAndReturnNumberOfAffectedRows() {
        PaymentState newState = PaymentState.NEW;
        testPayment.insert(testContext.getJdbi());
        int numOfUpdatedPayments = paymentDao.updateState(testPayment.getId(), newState);
        Map<String, Object> transactionAfterUpdate = testContext.getDatabaseTestHelper().getPaymentById(testPayment.getId());
        assertThat(numOfUpdatedPayments, is(1));
        assertThat(transactionAfterUpdate.get("id"), is(testPayment.getId()));
        assertThat(transactionAfterUpdate.get("external_id"), is(testPayment.getExternalId()));
        assertThat(transactionAfterUpdate.get("mandate_id"), is(testMandate.getId()));
        assertThat(transactionAfterUpdate.get("description"), is(testPayment.getDescription()));
        assertThat(transactionAfterUpdate.get("reference"), is(testPayment.getReference()));
        assertThat(transactionAfterUpdate.get("amount"), is(AMOUNT));
        assertThat(transactionAfterUpdate.get("state"), is(newState.toString()));
        assertThat((Timestamp) transactionAfterUpdate.get("created_date"), isDate(testPayment.getCreatedDate()));
    }

    @Test
    public void shouldNotUpdateAnythingIfPaymentDoesNotExist() {
        int numOfUpdatedPayments = paymentDao.updateState(34L, PaymentState.NEW);
        assertThat(numOfUpdatedPayments, is(0));
    }
    
    @Test
    public void findAllPaymentsBySetOfStatesAndCreationTime_shouldFindThreePayments() {
        aPaymentFixture().withMandateFixture(testMandate).withState(PaymentState.NEW)
                .withCreatedDate(ZonedDateTime.now().minusMinutes(91L)).insert(testContext.getJdbi());
        
        aPaymentFixture().withMandateFixture(testMandate).withState(PaymentState.NEW)
                .withCreatedDate(ZonedDateTime.now().minusMinutes(91L)).insert(testContext.getJdbi());
        
        aPaymentFixture().withMandateFixture(testMandate).withState(PaymentState.NEW)
                .withCreatedDate(ZonedDateTime.now().minusMinutes(91L)).insert(testContext.getJdbi());
        
        PaymentStatesGraph paymentStatesGraph = new PaymentStatesGraph();
        Set<PaymentState> states = paymentStatesGraph.getPriorStates(PaymentState.PENDING);
        List<Payment> payments = paymentDao.findAllPaymentsBySetOfStatesAndCreationTime(states, ZonedDateTime.now().minusMinutes(90L));
        assertThat(payments.size(), is(3));
    }

    @Test
    public void findAllPaymentsBySetOfStatesAndCreationTime_shouldNotFindPayment_TooEarly() {
        aPaymentFixture().withMandateFixture(testMandate).withState(PaymentState.NEW)
                .withCreatedDate(ZonedDateTime.now()).insert(testContext.getJdbi());

        PaymentStatesGraph paymentStatesGraph = new PaymentStatesGraph();
        Set<PaymentState> states = paymentStatesGraph.getPriorStates(PaymentState.PENDING);
        List<Payment> payments = paymentDao.findAllPaymentsBySetOfStatesAndCreationTime(states, ZonedDateTime.now().minusMinutes(90L));
        assertThat(payments.size(), is(0));
    }

    @Test
    public void findAllPaymentsBySetOfStatesAndCreationTime_shouldNotFindPayment_WrongState() {
        aPaymentFixture().withMandateFixture(testMandate).withState(PaymentState.PENDING)
                .withCreatedDate(ZonedDateTime.now()).insert(testContext.getJdbi());

        PaymentStatesGraph paymentStatesGraph = new PaymentStatesGraph();
        Set<PaymentState> states = paymentStatesGraph.getPriorStates(PaymentState.PENDING);
        List<Payment> payments = paymentDao.findAllPaymentsBySetOfStatesAndCreationTime(states, ZonedDateTime.now().minusMinutes(90L));
        assertThat(payments.size(), is(0));
    }
    

    private PaymentFixture generateNewPaymentFixture(MandateFixture mandateFixture,
                                                     PaymentState paymentState,
                                                     long amount) {
        return aPaymentFixture()
                .withMandateFixture(mandateFixture)
                .withAmount(amount)
                .withState(paymentState);
    }

}
