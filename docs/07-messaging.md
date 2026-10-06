# 7. Messaging: parent SMS, and OTP by WhatsApp or SMS

There are two kinds of message. They work differently.

| | Parent bus SMS | Staff login OTP |
|---|---|---|
| Goes to | Parents | Staff who log in |
| Channel | SMS | WhatsApp first, SMS if that fails |
| How many a day | About 4 per child | A few a month |
| Must arrive in | A minute or two | A few seconds |
| How it is sent | Through a queue table | Directly, at once |
| Stored in | `message_outbox` | `otp_code` (never the code itself) |

## Part A. Parent bus SMS

### The flow

1. An attendant's tap is saved as a `boarding_event`.
2. In the **same database transaction**, the server writes rows into `message_outbox` with status `QUEUED`. One row per parent phone that has "bus SMS" on.
3. A background job (`OutboxWorker`, every 5 seconds) picks up `QUEUED` rows and sends them.
4. Success → `SENT`, with the provider's id and the time. Failure → try again later.

Why a queue table and not "send now"? Real-life picture: the attendant drops letters into the school's letter box (the table). The postman (the job) comes every 5 seconds. If the postman is sick for 10 minutes, the letters wait in the box. None is lost. And the attendant never waits for the postman.

### Which class gets which SMS

This is a promise the school makes to parents. It lives in one class, `SmsPolicy`, with a unit test.

| Class | Boarded morning | Reached school | Boarded evening | Reached home |
|---|---|---|---|---|
| Nursery to 5 | yes | yes | yes | yes |
| 6 to 8 | yes | yes | yes | yes |
| 9 to 10 | no | yes | yes | no |
| 11 to 12 | no | no | no | no |

Example: Aryan (Class 3) and Siya (Class 11) are brother and sister with the same mother. One morning tap for each. The mother gets one SMS, about Aryan only.

### When no SMS is created

- The outcome is `ABSENT`, `NOT_TRAVELLING` or `CLEARED`.
- The class rule says no.
- The tap is for a day that is not today. Example: a phone was off all day and sends yesterday's taps this morning. Save the taps, send nothing.
- An SMS for this child, day and event already exists (the unique index stops it).
- The parent's link has `sms_enabled = false`.

### The texts

Hindi, short, and different for a boy and a girl, because Hindi verbs change. So there are 8 templates.

| Code | Text |
|---|---|
| `BOARDED_MORNING_M` | `{name} सुबह की बस में चढ़ गया — {time}। MUH Jain School` |
| `BOARDED_MORNING_F` | `{name} सुबह की बस में चढ़ गई — {time}। MUH Jain School` |
| `REACHED_SCHOOL_M` | `{name} स्कूल पहुँच गया — {time}। MUH Jain School` |
| `REACHED_SCHOOL_F` | `{name} स्कूल पहुँच गई — {time}। MUH Jain School` |
| `BOARDED_EVENING_M` | `{name} छुट्टी की बस में चढ़ गया — {time}। MUH Jain School` |
| `BOARDED_EVENING_F` | `{name} छुट्टी की बस में चढ़ गई — {time}। MUH Jain School` |
| `REACHED_HOME_M` | `{name} अपने स्टॉप पर उतर गया — {time}। MUH Jain School` |
| `REACHED_HOME_F` | `{name} अपने स्टॉप पर उतर गई — {time}। MUH Jain School` |

- `{name}` is the child's first name. `{time}` is the tap time like `7:42`.
- A Hindi SMS holds 70 characters in one part. Keep every final text at 70 or less, or it costs double. `SmsTemplatesTest` checks this with a 12-letter name.
- The text sent must match the text approved on DLT exactly. So the texts live in the `message_template` table with the provider's template id, not in Java code.

### Sending

```java
public interface SmsSender {
    SendResult send(String phone, String body, String providerTemplateId) throws SmsSendException;
}
```

| Class | Profile | What it does |
|---|---|---|
| `LogSmsSender` | dev, test | Writes the SMS to the log. Row status becomes `TEST_ONLY`. |
| one real class, for example `Msg91SmsSender` | prod | Calls the provider's HTTP API |

Pick the class with the setting `app.messaging.sms-provider`. To change provider later, write one new class. Nothing else changes.

### Retry

- On failure: `attempts + 1`, keep `QUEUED`, save the error text.
- After 3 failures: status `FAILED`. It shows on the Messages screen in red.
- The worker takes 50 rows at a time, oldest first, with `for update skip locked`. So two workers can never send the same row.
- A boarding SMS older than 2 hours is not sent any more. Mark it `FAILED` with error "too old". A parent should not get "boarded at 7:42" at noon.

### Before real SMS can go out (not code)

In India, a business SMS needs DLT registration: the school registers, gets a sender id, and gets each of the 8 texts approved. The roadmap says this takes one to two weeks. Start it in Phase 0. Until then everything runs with `LogSmsSender`.

## Part B. Login OTP

Details of the login flow are in `docs/04-login-otp-jwt.md`. This part is only about delivery.

```java
public interface OtpSender {
    String channel();
    void send(String phone, String code) throws OtpSendException;
}
```

| Class | Channel | Needs |
|---|---|---|
| `LogOtpSender` | LOG | nothing. Dev and test only. |
| `WhatsAppOtpSender` | WHATSAPP | A WhatsApp Business account through a provider and one approved authentication template |
| `SmsOtpSender` | SMS | The same SMS provider as parent SMS, and one approved OTP template |

`OtpDeliveryService` tries them in the order of `app.otp.channels`. Example with `whatsapp,sms`: WhatsApp gives an error → the same code goes by SMS → the login screen says "Code sent by SMS".

Rules:

- The code is sent directly, not through `message_outbox`. A login cannot wait in a queue.
- Give each provider call a timeout of 5 seconds. A slow provider must not hang the login.
- The code is never written to a table or a log (except `LogOtpSender` in dev).
- The app must refuse to start in the `prod` profile if the only OTP channel is `log`.

## Costs to keep in mind

- Parent SMS: about 255 bus children × up to 4 messages × parents' phones, every school day. This is the main message cost. The class rule and "one SMS per event" keep it down.
- OTP: about 15 users × one login a month. Very small.

Ask the provider for the price per SMS and per WhatsApp authentication message before choosing.

## What the Messages screen shows

`GET /api/v1/messages?date=2026-10-07` returns: time, child, phone (last 4 digits visible), event, text, status, error. A parent says "I got no message" → the office searches the child and sees `FAILED: invalid number`.
