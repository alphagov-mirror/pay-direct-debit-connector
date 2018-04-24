package uk.gov.pay.directdebit.tokens.fixtures;

import org.apache.commons.lang3.RandomUtils;
import org.jdbi.v3.core.Jdbi;
import uk.gov.pay.directdebit.common.fixtures.DbFixture;
import uk.gov.pay.directdebit.payments.model.Token;

public class TokenFixture implements DbFixture<TokenFixture, Token> {
    private Long id = RandomUtils.nextLong(1, 99999);
    private String token = "3c9fee80-977a-4da5-a003-4872a8cf95b6";
    private Long paymentRequestId = RandomUtils.nextLong(1, 99999);

    private TokenFixture() {
    }

    public static TokenFixture aTokenFixture() {
        return new TokenFixture();
    }

    public TokenFixture withPaymentRequestId(Long paymentRequestId) {
        this.paymentRequestId = paymentRequestId;
        return this;
    }

    public TokenFixture withToken(String token) {
        this.token = token;
        return this;
    }

    public String getToken() {
        return token;
    }

    public Long getPaymentRequestId() {
        return paymentRequestId;
    }

    @Override
    public TokenFixture insert(Jdbi jdbi) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO tokens(payment_request_id, secure_redirect_token) VALUES (:payment_request_id, :secure_redirect_token)")
                        .bind("payment_request_id", paymentRequestId)
                        .bind("secure_redirect_token", token)
                        .execute()
        );
        return this;
    }

    @Override
    public Token toEntity() {
        return new Token(id, token, paymentRequestId);
    }

}
