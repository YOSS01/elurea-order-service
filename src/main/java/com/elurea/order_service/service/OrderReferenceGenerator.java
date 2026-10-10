package com.elurea.order_service.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates order references customers can read over the phone, e.g. "EL260109-K7Q2MX":
 * date + 6 random characters from an alphabet without look-alikes (no 0/O, 1/I/L).
 * About 887 million combinations per day; the unique index on the column guards the rest.
 */
@Component
public class OrderReferenceGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int RANDOM_LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder reference = new StringBuilder("EL").append(LocalDate.now(ZoneOffset.UTC).format(DATE)).append('-');
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            reference.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return reference.toString();
    }
}
