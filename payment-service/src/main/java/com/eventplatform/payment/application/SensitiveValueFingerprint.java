package com.eventplatform.payment.application;
import java.nio.charset.StandardCharsets; import java.security.*; import java.util.HexFormat;
final class SensitiveValueFingerprint { private SensitiveValueFingerprint(){} static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}} }
