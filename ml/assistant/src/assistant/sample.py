"""A small database with two businesses, in SQLite, shaped like the bank's tables the tools read.

The tests and the evaluation use it, so neither needs PostgreSQL or the backend to say whether the assistant looks
up the right business and reports its numbers.
"""

from datetime import date, timedelta

from sqlalchemy import Engine, create_engine, text

JACKS_CAFE = "11111111-1111-1111-1111-111111111111"
OTHER_SHOP = "22222222-2222-2222-2222-222222222222"

SCHEMA = [
    "CREATE TABLE bank_account (id TEXT PRIMARY KEY, business_id TEXT, kind TEXT, name TEXT, iban TEXT, "
    "balance NUMERIC, status TEXT, created_at TEXT)",
    "CREATE TABLE journal_entry (id TEXT PRIMARY KEY, description TEXT)",
    "CREATE TABLE posting (id TEXT PRIMARY KEY, journal_entry_id TEXT, account_id TEXT, amount NUMERIC, "
    "booked_at TEXT)",
    "CREATE TABLE expense (id TEXT PRIMARY KEY, business_id TEXT, issue_date DATE, total_amount NUMERIC, "
    "vat_amount NUMERIC, category TEXT)",
    "CREATE TABLE loan_application (id TEXT PRIMARY KEY, business_id TEXT, amount NUMERIC, term_months INT, "
    "purpose TEXT, monthly_instalment NUMERIC, status TEXT, submitted_at TEXT, created_at TEXT)",
    "CREATE TABLE loan_instalment (id TEXT PRIMARY KEY, loan_application_id TEXT, due_on DATE, amount NUMERIC, "
    "status TEXT)",
]


def database(today: date) -> Engine:
    engine = create_engine("sqlite://")
    days = [(today - timedelta(days=n)).isoformat() for n in (5, 20, 40, 200)]
    with engine.begin() as db:
        for statement in SCHEMA:
            db.execute(text(statement))
        db.execute(text(f"""INSERT INTO bank_account VALUES
            ('a1', '{JACKS_CAFE}', 'CUSTOMER', 'Τρεχούμενος', 'GR1601101250000000012300695', 8420.50, 'ACTIVE',
             '2025-01-01'),
            ('a2', '{OTHER_SHOP}', 'CUSTOMER', 'Τρεχούμενος', 'GR1601101250000000099900111', 99999.00, 'ACTIVE',
             '2025-01-01')
        """))  # fmt: skip
        db.execute(text("INSERT INTO journal_entry VALUES ('e1', 'Πώληση ημέρας'), ('e2', 'Ενοίκιο καταστήματος')"))
        db.execute(text(f"""INSERT INTO posting VALUES
            ('p1', 'e1', 'a1', 640.00, '{days[0]}T18:00:00'),
            ('p2', 'e2', 'a1', -1200.00, '{days[1]}T09:00:00'),
            ('p3', 'e1', 'a2', 5000.00, '{days[0]}T10:00:00')
        """))  # fmt: skip
        db.execute(text(f"""INSERT INTO expense VALUES
            ('x1', '{JACKS_CAFE}', '{days[0]}', 1200.00, 0.00, 'RENT'),
            ('x2', '{JACKS_CAFE}', '{days[1]}', 310.00, 59.35, 'GROCERIES'),
            ('x3', '{JACKS_CAFE}', '{days[2]}', 95.40, 18.26, 'UTILITIES'),
            ('x4', '{JACKS_CAFE}', '{days[3]}', 800.00, 153.23, 'EQUIPMENT'),
            ('x5', '{OTHER_SHOP}', '{days[0]}', 7777.00, 0.00, 'RENT')
        """))  # fmt: skip
        db.execute(text(f"""INSERT INTO loan_application VALUES
            ('l1', '{JACKS_CAFE}', 20000.00, 36, 'EQUIPMENT', 617.54, 'DISBURSED', '2026-06-01', '2026-06-01')
        """))  # fmt: skip
        next_month = (today + timedelta(days=12)).isoformat()
        db.execute(text(f"INSERT INTO loan_instalment VALUES ('i1', 'l1', '{next_month}', 617.54, 'SCHEDULED')"))
    return engine
