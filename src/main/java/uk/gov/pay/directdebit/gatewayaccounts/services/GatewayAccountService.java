package uk.gov.pay.directdebit.gatewayaccounts.services;

import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.directdebit.gatewayaccounts.api.CreateGatewayAccountRequest;
import uk.gov.pay.directdebit.gatewayaccounts.api.GatewayAccountResponse;
import uk.gov.pay.directdebit.gatewayaccounts.api.PatchGatewayAccountValidator;
import uk.gov.pay.directdebit.gatewayaccounts.dao.GatewayAccountDao;
import uk.gov.pay.directdebit.gatewayaccounts.exception.GatewayAccountNotFoundException;
import uk.gov.pay.directdebit.gatewayaccounts.model.GatewayAccount;
import uk.gov.pay.directdebit.gatewayaccounts.model.GoCardlessOrganisationId;
import uk.gov.pay.directdebit.gatewayaccounts.model.PaymentProviderAccessToken;
import uk.gov.pay.directdebit.payments.model.Payment;

import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GatewayAccountService {

    private GatewayAccountDao gatewayAccountDao;

    private static final Splitter COMMA_SEPARATOR = Splitter.on(',').trimResults().omitEmptyStrings();
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAccountService.class);

    private PatchGatewayAccountValidator validator = new PatchGatewayAccountValidator();

    @Inject
    public GatewayAccountService(GatewayAccountDao gatewayAccountDao) {
        this.gatewayAccountDao = gatewayAccountDao;
    }

    public GatewayAccount getGatewayAccountForId(String accountExternalId) {
        return gatewayAccountDao
                .findByExternalId(accountExternalId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(accountExternalId));
    }

    GatewayAccount getGatewayAccountFor(Payment payment) {
        return gatewayAccountDao
                .findById(payment.getMandate().getGatewayAccount().getId())
                .orElseThrow(() -> new GatewayAccountNotFoundException(payment.getMandate().getGatewayAccount().getId().toString()));
    }

    public List<GatewayAccountResponse> getAllGatewayAccounts(String externalAccountIdsArg, UriInfo uriInfo) {
        List<String> externalAccountIds = COMMA_SEPARATOR.splitToList(externalAccountIdsArg);

        return (
                externalAccountIds.isEmpty()
                        ? gatewayAccountDao.findAll()
                        : gatewayAccountDao.find(externalAccountIds)
        )
                .stream()
                .map(gatewayAccount -> GatewayAccountResponse.from(gatewayAccount).withSelfLink(uriInfo))
                .collect(Collectors.toList());
    }

    public GatewayAccount create(CreateGatewayAccountRequest request) {
        GatewayAccount gatewayAccount = new GatewayAccount(
                request.getPaymentProvider(),
                request.getType(),
                request.getDescription(),
                request.getAnalyticsId(),
                request.getAccessToken(),
                request.getOrganisation());

        Long id = gatewayAccountDao.insert(gatewayAccount);
        gatewayAccount.setId(id);
        LOGGER.info("Created Gateway Account with id {}", id);
        return gatewayAccount;
    }

    public GatewayAccount patch(String externalId, List<Map<String, String>> request) {
        validator.validatePatchRequest(externalId, request);
        PaymentProviderAccessToken accessToken = null;
        GoCardlessOrganisationId organisation = null;
        for (Map<String, String> operation : request) {
            if (operation.get("path").equals("access_token")) {
                accessToken = PaymentProviderAccessToken.of(operation.get("value"));
            } else {
                organisation = GoCardlessOrganisationId.valueOf(operation.get("value"));
            }
        }
        
        gatewayAccountDao.updateAccessTokenAndOrganisation(externalId, accessToken, organisation);
        
        return this.getGatewayAccountForId(externalId);
    }
}
