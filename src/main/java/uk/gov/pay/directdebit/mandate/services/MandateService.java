package uk.gov.pay.directdebit.mandate.services;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import uk.gov.pay.directdebit.app.config.DirectDebitConfig;
import uk.gov.pay.directdebit.app.config.LinksConfig;
import uk.gov.pay.directdebit.app.logger.PayLoggerFactory;
import uk.gov.pay.directdebit.common.util.RandomIdGenerator;
import uk.gov.pay.directdebit.gatewayaccounts.dao.GatewayAccountDao;
import uk.gov.pay.directdebit.gatewayaccounts.exception.GatewayAccountNotFoundException;
import uk.gov.pay.directdebit.gatewayaccounts.model.PaymentProvider;
import uk.gov.pay.directdebit.mandate.api.CreateMandateResponse;
import uk.gov.pay.directdebit.mandate.api.DirectDebitInfoFrontendResponse;
import uk.gov.pay.directdebit.mandate.api.GetMandateResponse;
import uk.gov.pay.directdebit.mandate.dao.MandateDao;
import uk.gov.pay.directdebit.mandate.exception.MandateNotFoundException;
import uk.gov.pay.directdebit.mandate.exception.WrongNumberOfTransactionsForOneOffMandateException;
import uk.gov.pay.directdebit.mandate.model.ConfirmationDetails;
import uk.gov.pay.directdebit.mandate.model.Mandate;
import uk.gov.pay.directdebit.mandate.model.MandateState;
import uk.gov.pay.directdebit.mandate.model.MandateStatesGraph;
import uk.gov.pay.directdebit.mandate.model.MandateType;
import uk.gov.pay.directdebit.notifications.services.UserNotificationService;
import uk.gov.pay.directdebit.payers.model.AccountNumber;
import uk.gov.pay.directdebit.payers.model.SortCode;
import uk.gov.pay.directdebit.payments.exception.InvalidMandateTypeException;
import uk.gov.pay.directdebit.payments.model.DirectDebitEvent;
import uk.gov.pay.directdebit.payments.model.DirectDebitEvent.SupportedEvent;
import uk.gov.pay.directdebit.payments.model.Token;
import uk.gov.pay.directdebit.payments.model.Transaction;
import uk.gov.pay.directdebit.payments.services.DirectDebitEventService;
import uk.gov.pay.directdebit.payments.services.TransactionService;
import uk.gov.pay.directdebit.tokens.exception.TokenNotFoundException;
import uk.gov.pay.directdebit.tokens.model.TokenExchangeDetails;
import uk.gov.pay.directdebit.tokens.services.TokenService;

import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static uk.gov.pay.directdebit.common.util.URIBuilder.createLink;
import static uk.gov.pay.directdebit.common.util.URIBuilder.nextUrl;
import static uk.gov.pay.directdebit.common.util.URIBuilder.selfUriFor;
import static uk.gov.pay.directdebit.payments.model.DirectDebitEvent.SupportedEvent.DIRECT_DEBIT_DETAILS_CONFIRMED;
import static uk.gov.pay.directdebit.payments.model.DirectDebitEvent.SupportedEvent.MANDATE_ACTIVE;
import static uk.gov.pay.directdebit.payments.model.DirectDebitEvent.SupportedEvent.MANDATE_CANCELLED;
import static uk.gov.pay.directdebit.payments.model.DirectDebitEvent.SupportedEvent.MANDATE_FAILED;
import static uk.gov.pay.directdebit.payments.model.DirectDebitEvent.SupportedEvent.MANDATE_PENDING;
import static uk.gov.pay.directdebit.payments.model.DirectDebitEvent.SupportedEvent.TOKEN_EXCHANGED;
import static uk.gov.pay.directdebit.payments.model.DirectDebitEvent.Type.MANDATE;

public class MandateService {

    private static final Logger LOGGER = PayLoggerFactory.getLogger(MandateService.class);
    private final MandateDao mandateDao;
    private final LinksConfig linksConfig;
    private final GatewayAccountDao gatewayAccountDao;
    private final TokenService tokenService;
    private final TransactionService transactionService;
    private final DirectDebitEventService directDebitEventService;
    private final UserNotificationService userNotificationService;

    @Inject
    public MandateService(
            DirectDebitConfig directDebitConfig,
            MandateDao mandateDao, GatewayAccountDao gatewayAccountDao,
            TokenService tokenService,
            TransactionService transactionService,
            DirectDebitEventService directDebitEventService,
            UserNotificationService userNotificationService) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.tokenService = tokenService;
        this.transactionService = transactionService;
        this.directDebitEventService = directDebitEventService;
        this.mandateDao = mandateDao;
        this.userNotificationService = userNotificationService;
        this.linksConfig = directDebitConfig.getLinks();
    }

    public Mandate createMandate(Map<String, String> mandateRequestMap, String accountExternalId, MandateType mandateType) {
        return gatewayAccountDao.findByExternalId(accountExternalId)
                .map(gatewayAccount -> {
                    // TODO:
                    // when we introduce GoCardless gateway accounts to work with create mandate,
                    // then modify appropriate mandate reference values
                    String mandateReference = PaymentProvider.SANDBOX.equals(gatewayAccount.getPaymentProvider()) ?
                            RandomStringUtils.randomAlphanumeric(18) :
                            "gocardless-default";

                    Mandate mandate = new Mandate(
                            null,
                            gatewayAccount,
                            mandateType,
                            RandomIdGenerator.newId(),
                            mandateReference,
                            mandateRequestMap.get("service_reference"),
                            MandateState.CREATED,
                            mandateRequestMap.get("return_url"),
                            ZonedDateTime.now(ZoneOffset.UTC),
                            null);
                    LOGGER.info("Creating mandate external id {}", mandate.getExternalId());
                    Long id = mandateDao.insert(mandate);
                    mandate.setId(id);
                    return mandate;
                })
                .orElseThrow(() -> {
                    LOGGER.error("Gateway account with id {} not found", accountExternalId);
                    return new GatewayAccountNotFoundException(accountExternalId);
                });
    }

    public TokenExchangeDetails getMandateFor(String token) {
        Mandate mandate = mandateDao
                .findByTokenId(token)
                .map(mandateForToken -> {
                    Mandate newMandate = updateStateFor(mandateForToken, TOKEN_EXCHANGED);
                    directDebitEventService.registerTokenExchangedEventFor(newMandate);
                    return newMandate;
                })
                .orElseThrow(TokenNotFoundException::new);
        String transactionExternalId = null;
        if (mandate.getType().equals(MandateType.ONE_OFF)) {
            List<Transaction> transactionsForMandate = transactionService
                    .findTransactionsForMandate(mandate.getExternalId());
            if (transactionsForMandate.size() != 1) {
                throw new WrongNumberOfTransactionsForOneOffMandateException("Found zero or multiple transactions for one off mandate with external id " + mandate.getExternalId());
            }
            transactionExternalId = transactionsForMandate.get(0).getExternalId();
        }
        return new TokenExchangeDetails(mandate, transactionExternalId);

    }

    public DirectDebitInfoFrontendResponse populateGetMandateWithTransactionResponseForFrontend(String accountExternalId, String transactionExternalId) {
        Transaction transaction = transactionService
                .findTransactionForExternalId(transactionExternalId);
        Mandate mandate = transaction.getMandate();
        return new DirectDebitInfoFrontendResponse(
                mandate.getExternalId(),
                mandate.getGatewayAccount().getId(),
                accountExternalId,
                mandate.getState().toExternal(),
                mandate.getReturnUrl(),
                mandate.getMandateReference(),
                mandate.getType().toString(),
                mandate.getCreatedDate().toString(),
                mandate.getPayer(),
                transaction
        );
    }

    public DirectDebitInfoFrontendResponse populateGetMandateResponseForFrontend(String accountExternalId, String mandateExternalId) {
        Mandate mandate = findByExternalId(mandateExternalId);
        return new DirectDebitInfoFrontendResponse(
                mandate.getExternalId(),
                mandate.getGatewayAccount().getId(),
                accountExternalId,
                mandate.getState().toExternal(),
                mandate.getReturnUrl(),
                mandate.getMandateReference(),
                mandate.getType().toString(),
                mandate.getCreatedDate().toString(),
                mandate.getPayer(),
                null
        );
    }

    public GetMandateResponse populateGetMandateResponse(String accountExternalId, String mandateExternalId, UriInfo uriInfo) {
        Mandate mandate = findByExternalId(mandateExternalId);
        List<Map<String, Object>> dataLinks = createLinks(mandate, accountExternalId, uriInfo);

        return new GetMandateResponse(
                mandateExternalId,
                mandate.getType(),
                mandate.getReturnUrl(),
                dataLinks,
                mandate.getState().toExternal(),
                mandate.getServiceReference(),
                mandate.getMandateReference());
    }

    public CreateMandateResponse createMandate(Map<String, String> mandateRequestMap, String accountExternalId, UriInfo uriInfo) {
        MandateType mandateType = MandateType.fromString(mandateRequestMap.get("agreement_type"));
        if (MandateType.ONE_OFF.equals(mandateType)) {
            throw new InvalidMandateTypeException(mandateType);
        }
        Mandate mandate = createMandate(mandateRequestMap, accountExternalId, mandateType);
        String mandateExternalId = mandate.getExternalId();
        List<Map<String, Object>> dataLinks = createLinks(mandate, accountExternalId, uriInfo);

        return new CreateMandateResponse(
                mandateExternalId,
                mandate.getType(),
                mandate.getReturnUrl(),
                mandate.getCreatedDate().toString(),
                mandate.getState().toExternal(),
                dataLinks,
                mandate.getServiceReference(),
                mandate.getMandateReference());
    }

    public Mandate findByExternalId(String externalId) {
        return mandateDao
                .findByExternalId(externalId)
                .orElseThrow(() -> new MandateNotFoundException(externalId));
    }

    public Mandate findById(Long id) {
        return mandateDao
                .findById(id)
                .orElseThrow(() -> new MandateNotFoundException(id.toString()));
    }

    public List<Mandate> findAllMandatesBySetOfStatesAndMaxCreationTime(Set<MandateState> states, ZonedDateTime maxCreationTime) {
        return mandateDao.findAllMandatesBySetOfStatesAndMaxCreationTime(states, maxCreationTime);
    }

    public DirectDebitEvent mandateFailedFor(Mandate mandate) {
        Mandate newMandate = updateStateFor(mandate, MANDATE_FAILED);
        userNotificationService.sendMandateFailedEmailFor(newMandate);
        return directDebitEventService.registerMandateFailedEventFor(newMandate);
    }

    public DirectDebitEvent mandateCancelledFor(Mandate mandate) {
        Mandate newMandate = updateStateFor(mandate, MANDATE_CANCELLED);
        userNotificationService.sendMandateCancelledEmailFor(newMandate);
        return directDebitEventService.registerMandateCancelledEventFor(newMandate);
    }

    public DirectDebitEvent mandatePendingFor(Mandate mandate) {
        Mandate newMandate = updateStateFor(mandate, MANDATE_PENDING);
        return directDebitEventService.registerMandatePendingEventFor(newMandate);
    }

    public DirectDebitEvent awaitingDirectDebitDetailsFor(Mandate mandate) {
        return directDebitEventService.registerAwaitingDirectDebitDetailsEventFor(mandate);
    }

    public DirectDebitEvent mandateActiveFor(Mandate mandate) {
        updateStateFor(mandate, MANDATE_ACTIVE);
        return directDebitEventService.registerMandateActiveEventFor(mandate);
    }

    public DirectDebitEvent mandateExpiredFor(Mandate mandate) {
        Mandate updatedMandate = updateStateFor(mandate, SupportedEvent.MANDATE_EXPIRED_BY_SYSTEM);
        return directDebitEventService.registerMandateExpiredEventFor(updatedMandate);
    }

    public Mandate receiveDirectDebitDetailsFor(String mandateExternalId) {
        Mandate mandate = findByExternalId(mandateExternalId);
        directDebitEventService.registerDirectDebitReceivedEventFor(mandate);
        return mandate;
    }

    public Mandate confirmedDirectDebitDetailsFor(String mandateExternalId) {
        Mandate mandate = findByExternalId(mandateExternalId);
        updateStateFor(mandate, DIRECT_DEBIT_DETAILS_CONFIRMED);
        directDebitEventService.registerDirectDebitConfirmedEventFor(mandate);
        return mandate;
    }

    public Mandate payerCreatedFor(Mandate mandate) {
        directDebitEventService.registerPayerCreatedEventFor(mandate);
        return mandate;
    }

    public DirectDebitEvent payerEditedFor(Mandate mandate) {
        return directDebitEventService.registerPayerEditedEventFor(mandate);
    }

    private Mandate updateStateFor(Mandate mandate, SupportedEvent event) {
        MandateState newState = MandateStatesGraph.getStates().getNextStateForEvent(mandate.getState(),
                event);
        mandateDao.updateState(mandate.getId(), newState);
        LOGGER.info("Updating mandate {} - from {} to {}",
                mandate.getExternalId(),
                mandate.getState(),
                newState);
        mandate.setState(newState);
        return mandate;
    }

    public Optional<DirectDebitEvent> findMandatePendingEventFor(Mandate mandate) {
        return directDebitEventService.findBy(mandate.getId(), MANDATE, MANDATE_PENDING);
    }

    public DirectDebitEvent changePaymentMethodFor(String mandateExternalId) {
        Mandate mandate = findByExternalId(mandateExternalId);
        if (MandateType.ONE_OFF.equals(mandate.getType())) {
            Transaction transaction = retrieveTransactionForOneOffMandate(mandateExternalId);
            transactionService.paymentMethodChangedFor(transaction);
        }
        Mandate newMandate = updateStateFor(mandate, SupportedEvent.PAYMENT_CANCELLED_BY_USER_NOT_ELIGIBLE);
        return directDebitEventService.registerPaymentMethodChangedEventFor(newMandate);
    }

    public DirectDebitEvent cancelMandateCreation(String mandateExternalId) {
        Mandate mandate = findByExternalId(mandateExternalId);
        if (MandateType.ONE_OFF.equals(mandate.getType())) {
            Transaction transaction = retrieveTransactionForOneOffMandate(mandateExternalId);
            transactionService.paymentCancelledFor(transaction);
        }
        Mandate newMandate = updateStateFor(mandate, SupportedEvent.PAYMENT_CANCELLED_BY_USER);
        return directDebitEventService.registerMandateCancelledEventFor(newMandate);
    }

    private Transaction retrieveTransactionForOneOffMandate(String mandateExternalId) {
        List<Transaction> transactions = transactionService.findTransactionsForMandate(mandateExternalId);
        if (transactions.size() != 1) {
            throw new WrongNumberOfTransactionsForOneOffMandateException("Found multiple transactions for one off mandate with external id " + mandateExternalId);
        }
        return transactions.get(0);
    }

    private List<Map<String, Object>> createLinks(Mandate mandate, String accountExternalId, UriInfo uriInfo) {
        List<Map<String, Object>> dataLinks = new ArrayList<>();

        dataLinks.add(createLink("self", GET, selfUriFor(uriInfo,
                "/v1/api/accounts/{accountId}/mandates/{mandateExternalId}",
                accountExternalId,
                mandate.getExternalId())));

        if (!mandate.getState().toExternal().isFinished()) {
            Token token = tokenService.generateNewTokenFor(mandate);
            dataLinks.add(createLink("next_url",
                    GET,
                    nextUrl(linksConfig.getFrontendUrl(), "secure", token.getToken())));
            dataLinks.add(createLink("next_url_post",
                    POST,
                    nextUrl(linksConfig.getFrontendUrl(), "secure"),
                    APPLICATION_FORM_URLENCODED,
                    ImmutableMap.of("chargeTokenId", token.getToken())));
        }
        return dataLinks;
    }

    /**
     * Creates a mandate. If this is sandbox, also updates it to pending.
     *
     * @param mandateExternalId
     */
    public ConfirmationDetails confirm(String mandateExternalId, Map<String, String> confirmDetailsRequest) {
        SortCode sortCode = SortCode.of(confirmDetailsRequest.get("sort_code"));
        AccountNumber accountNumber = AccountNumber.of(confirmDetailsRequest.get("account_number"));
        Mandate mandate = confirmedDirectDebitDetailsFor(mandateExternalId);
        Transaction transaction = Optional
                .ofNullable(confirmDetailsRequest.get("transaction_external_id"))
                .map(transactionService::findTransactionForExternalId)
                .orElse(null);
        return new ConfirmationDetails(mandate, transaction, accountNumber, sortCode);
    }
}
