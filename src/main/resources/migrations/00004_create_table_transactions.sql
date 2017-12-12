--liquibase formatted sql

--changeset uk.gov.pay:add_table-transactions
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    payment_request_id BIGSERIAL NOT NULL,
    amount BIGINT NOT NULL,
    type TEXT NOT NULL,
    state TEXT NOT NULL,
    version INTEGER DEFAULT 0 NOT NULL
);
--rollback drop table transactions;

--changeset uk.gov.pay:add_index-transactions_payment_request_id
CREATE INDEX transactions_payment_request_id ON transactions(payment_request_id)
--rollback drop index transactions_payment_request_id;