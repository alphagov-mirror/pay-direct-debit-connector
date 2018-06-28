package uk.gov.pay.directdebit.events.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import uk.gov.pay.directdebit.payments.model.DirectDebitEvent;
import uk.gov.pay.directdebit.payments.params.DirectDebitEventSearchParams;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@AllArgsConstructor
public class DirectDebitEventsResponse {
    
    @JsonProperty("results")
    private final List<DirectDebitEvent> events;
    @JsonProperty
    private final Integer page;
    @JsonProperty
    private final int total;
    @JsonProperty
    private final int count;
    
    public DirectDebitEventsResponse(List<DirectDebitEvent> events, int page, int total) {
        this.events = events;
        this.total = total;
        this.page = page;
        this.count = events.size();
    }
}
