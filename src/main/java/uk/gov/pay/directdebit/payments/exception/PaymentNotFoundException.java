package uk.gov.pay.directdebit.payments.exception;

import uk.gov.pay.directdebit.common.exception.NotFoundException;
import uk.gov.pay.directdebit.gatewayaccounts.model.GoCardlessOrganisationId;
import uk.gov.pay.directdebit.gatewayaccounts.model.PaymentProvider;
import uk.gov.pay.directdebit.payments.model.PaymentProviderPaymentId;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import static java.lang.String.format;

public class PaymentNotFoundException extends NotFoundException {

    public PaymentNotFoundException(String externalId) {
        super((format("No payment found with external id %s", externalId)),
                ErrorIdentifier.GENERIC);
    }

    public PaymentNotFoundException(PaymentProvider provider, PaymentProviderPaymentId paymentId) {
        super(format("No payment found for provider %s with payment provider payment ID %s", provider, paymentId),
                ErrorIdentifier.GENERIC);
    }

    public PaymentNotFoundException(PaymentProviderPaymentId paymentProviderPaymentId, GoCardlessOrganisationId organisationId) {
        super(format("Couldn't find GoCardless payment %s for organisation %s", paymentProviderPaymentId, organisationId),
                ErrorIdentifier.GENERIC);
    }

}

