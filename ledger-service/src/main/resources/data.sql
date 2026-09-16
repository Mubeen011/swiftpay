INSERT INTO accounts (account_id, balance, currency)
VALUES ('ACC001', 5000.00, 'INR')
    ON CONFLICT (account_id) DO NOTHING;

INSERT INTO accounts (account_id, balance, currency)
VALUES ('ACC002', 1000.00, 'INR')
    ON CONFLICT (account_id) DO NOTHING;