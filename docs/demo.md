# A ten-minute demo

One loan from application to money in the account, seen from both sides, then the operations view. The
buttons are in Greek, as on screen; what to say is in the boxes.

## Before you start (5 minutes, not part of the ten)

```bash
docker compose --profile app down -v                # a clean start: only the demo data
docker compose --profile app up -d --build --wait   # about 3 minutes
```

Open three browser windows, so nobody signs out during the demo:

| Window | Sign in as | Password |
| --- | --- | --- |
| A, normal | jack@tameio.local | jack123 |
| B, private | analyst@tameio.local | analyst123 |
| C, another browser | manager@tameio.local | manager123 |

Keep Grafana open in a tab (http://localhost:3000) and have a receipt ready to upload
(`frontend/e2e/fixtures/receipt.pdf` works).

## 0:00 What it is (45 s)

Show the diagram at the top of the README.

> Tameio is a small, fictional bank for Greek small businesses with their books built in. The business keeps
> its money here, its receipts become an expense book, and when it asks for a loan the application fills
> itself in from those figures. On the bank's side, an analyst recommends and a manager decides, and they
> can never be the same person. It is one Spring Boot application split into modules, with a React app,
> a Python service for reading receipts, PostgreSQL and RabbitMQ.

## 0:45 The account and the ledger (1 min): window A, Jack

1. **Jack's Café** → tab **Λογαριασμοί**: the balance and a year of movements.

> Every movement is a journal entry whose lines add up to zero. The database itself refuses an entry that
> does not balance and any change to an old one, so a bug in the code cannot create or lose money. Entries
> lock the accounts they touch in a fixed order, so two at once never deadlock or overdraw.

## 1:45 A receipt read by AI (2 min): window A

1. Tab **Παραστατικά** → **Επιλογή αρχείων** → the receipt. It shows **Ανέβηκε**, then **Διαβάστηκε** within seconds.
2. Open it: the file on the left, what was read on the right, each field with how sure the reader was.
3. Change the category, press **Επιβεβαίωση**: it becomes **Ελέγχθηκε**.

> The upload is saved together with an event in the same transaction, and only after the commit does the
> event go to RabbitMQ: the outbox pattern, so no job is lost and none is sent for an upload that failed. A
> worker reads it with OCR outside any transaction, retries if the AI service is down, and sends it to a
> dead-letter queue in the end. The person confirms; every correction is kept as training data.

## 3:45 The books (45 s): window A

1. Tab **Λογιστικά**: spending by month, by category, VAT by quarter, the CSV for the accountant.

> The confirmed receipt is already here: the accounting module listens for the event; it never reads the
> documents module's tables.

## 4:30 The loan application (1 min): window A

1. Tab **Δάνεια** → **Νέα αίτηση** → 10.000 €, 24 months, a purpose → **Αποθήκευση πρόχειρης**.
2. **Υποβολή στην τράπεζα**: the stepper moves to **Υποβλήθηκε**.

> The figures come from the account and the expense book; they are taken again at submission, and the bank
> sees them as they were at that moment.

## 5:30 The analyst (1 min): window B

1. **Αιτήσεις δανείων** → Jack's Café → **Ανάληψη αξιολόγησης**.
2. Show the score on the right, then **Εισήγηση**: **Έγκριση**, a reason → **Υποβολή εισήγησης**.

> The score is four factors of 25 points, each explained in a sentence: repayment capacity, how long the
> account has been used, the cash cushion and the size of the loan. It helps the decision; it never makes it.
> The analyst has no decision button at all.

## 6:30 The manager (1 min 15 s): window C

1. Open the same case → **Απόφαση**: **Έγκριση**, a reason → **Οριστική απόφαση**.
2. **Εκταμίευση 10.000,00 €**.
3. Back in window A, refresh: the card shows **Εκταμιεύτηκε** and the next instalment; the account is 10.000 € higher.

> A manager decides only within their approval limit, and never on a case they recommended: the service
> refuses it, and a database constraint refuses it too, in case some later code forgets. If two managers press
> at once, the second gets "changed meanwhile" from optimistic locking. The payout is one ledger entry, in the
> same transaction as the status change. The Grinning Fox Pub's 150.000 € was decided by the administrator,
> because it is above the manager's limit.

## 7:45 Operations (1 min 30 s): Grafana

1. The Tameio dashboard: requests and latency per replica, the reading queue and dead letters, and the bank's
   figures: loans by status, money lent, instalments.
2. If there is time, in a terminal: `docker compose --profile app up -d --scale backend=3`, then the k6 test
   from the README: the requests line splits into three.

> With three instances, a nightly job would run three times and the demo data would load three times. A
> PostgreSQL advisory lock makes them happen once, and the job's update is idempotent anyway. Sessions are
> in a signed cookie checked against the database, so any instance can serve any user. Running three found a
> real bug: all of them tried to create the storage bucket at once.

## 9:15 AI where it pays (45 s): README, the field-extraction table

> I measured whether a small LLM reads receipts better than the rules: on receipts from shops no model had
> seen, the rules got all six fields right 69% of the time in 2 ms; the prompted model got 0% in 12 seconds.
> So it stayed out of the app. The fine-tuning notebook is there to try the fair version.

## 10:00 Questions

Good ones to expect, with the short answer:

- **Why not microservices?** One team, one database, transactions without sagas. The module boundaries are
  tested, so a module can leave later if it needs to.
- **What if RabbitMQ is down?** Uploads go on: the event waits in the database and is sent when it is back.
- **What is simplified?** See *Limits* in the README: instalments are marked due but not collected, there is
  no SCA, KYC or Τειρεσίας check, and all data is invented.
