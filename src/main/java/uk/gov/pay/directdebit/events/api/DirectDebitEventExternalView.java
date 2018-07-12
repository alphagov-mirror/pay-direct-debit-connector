package uk.gov.pay.directdebit.events.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import uk.gov.pay.directdebit.payments.model.CustomDateSerializer;
import uk.gov.pay.directdebit.payments.model.DirectDebitEvent;

import java.time.ZonedDateTime;

@Getter
public class DirectDebitEventExternalView {

    @JsonProperty("external_id")
    private final String externalId;

    @JsonProperty("mandate_external_id")
    private final String mandateExternalId;

    @JsonProperty("transaction_external_id")
    private final String transactionExternalId;

    @JsonProperty("event_type")
    private final DirectDebitEvent.Type eventType;

    @JsonProperty
    private final DirectDebitEvent.SupportedEvent event;

    @JsonProperty("event_date")
    @JsonSerialize(using = CustomDateSerializer.class)
    private final ZonedDateTime eventDate;
    
    public DirectDebitEventExternalView(DirectDebitEvent directDebitEvent) {
        this.externalId = directDebitEvent.getExternalId();
        this.mandateExternalId = directDebitEvent.getMandateExternalId();
        this.transactionExternalId = directDebitEvent.getTransactionExternalId();
        this.event = directDebitEvent.getEvent();
        this.eventType = directDebitEvent.getEventType();
        this.eventDate = directDebitEvent.getEventDate();
    }
}