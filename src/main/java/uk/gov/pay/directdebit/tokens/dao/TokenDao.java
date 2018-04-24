package uk.gov.pay.directdebit.tokens.dao;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import uk.gov.pay.directdebit.payments.model.Token;
import uk.gov.pay.directdebit.tokens.dao.mapper.TokenMapper;

import java.util.Optional;

@RegisterRowMapper(TokenMapper.class)
public interface TokenDao {
    @SqlQuery("SELECT * FROM tokens t WHERE t.secure_redirect_token = :token")
    Optional<Token> findByTokenId(@Bind("token") String token);

    @SqlQuery("SELECT * FROM tokens t WHERE t.payment_request_id = :paymentRequestId")
    Optional<Token> findByPaymentId(@Bind("paymentRequestId") Long chargeId);

    @SqlUpdate("INSERT INTO tokens(payment_request_id, secure_redirect_token) VALUES (:paymentRequestId, :token)")
    @GetGeneratedKeys
    Long insert(@BindBean Token token);

    @SqlUpdate("DELETE FROM tokens t WHERE t.secure_redirect_token = :token")
    int deleteToken(@Bind("token") String token);
}
