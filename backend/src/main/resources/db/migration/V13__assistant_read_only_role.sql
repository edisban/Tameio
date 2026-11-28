-- The assistant service reads the database to answer a business's questions, and never writes it. It connects as
-- its own role, which can only read the few tables its tools need; everything else (users, passwords, staff,
-- documents) it cannot see at all. The password is a Flyway placeholder, set from the environment.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'tameio_assistant') THEN
        CREATE ROLE tameio_assistant LOGIN PASSWORD '${assistant_password}';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO tameio_assistant;
GRANT SELECT ON bank_account, journal_entry, posting, expense, loan_application, loan_instalment TO tameio_assistant;
