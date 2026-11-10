package com.aiquestion.security;

/** Account IDs are meaningful only together with their account type. */
public record AccountIdentity(long accountId, AccountType accountType, long enterpriseId) {
  public String subject() {
    return accountType == AccountType.ADMIN ? Long.toString(accountId) : "employee:" + accountId;
  }
}
