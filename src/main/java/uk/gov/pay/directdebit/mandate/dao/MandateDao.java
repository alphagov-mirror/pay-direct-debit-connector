package uk.gov.pay.directdebit.mandate.dao;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import uk.gov.pay.directdebit.mandate.dao.mapper.MandateMapper;
import uk.gov.pay.directdebit.mandate.model.Mandate;
import uk.gov.pay.directdebit.mandate.model.MandateState;

import java.util.Optional;

@RegisterRowMapper(MandateMapper.class)
public interface MandateDao {

    @SqlUpdate("INSERT INTO mandates(payer_id, external_id, reference, state) VALUES (:payerId, :externalId, :reference, :state)")
    @GetGeneratedKeys
    Long insert(@BindBean Mandate mandate);

    @SqlQuery("SELECT * FROM mandates m JOIN payers p ON m.payer_id = p.id JOIN transactions t ON t.payment_request_id = p.payment_request_id WHERE t.id = :transactionId")
    Optional<Mandate> findByTransactionId(@Bind("transactionId") Long transactionId);

    @SqlUpdate("UPDATE mandates m SET state = :state WHERE m.id = :id")
    int updateState(@Bind("id") Long id, @Bind("state") MandateState mandateState);
}
