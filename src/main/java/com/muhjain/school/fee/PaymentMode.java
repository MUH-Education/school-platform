package com.muhjain.school.fee;

/** How the money came. The system only records it. It never sees card numbers or a UPI PIN. */
public enum PaymentMode {

	CASH, UPI, BANK_TRANSFER, CHEQUE

}
